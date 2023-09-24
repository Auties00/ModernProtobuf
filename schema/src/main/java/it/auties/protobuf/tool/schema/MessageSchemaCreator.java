package it.auties.protobuf.tool.schema;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.expr.AssignExpr.Operator;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.nodeTypes.NodeWithType;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufMessage;
import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.parser.statement.ProtobufEnumStatement;
import it.auties.protobuf.parser.statement.ProtobufFieldStatement;
import it.auties.protobuf.parser.statement.ProtobufMessageStatement;
import it.auties.protobuf.parser.statement.ProtobufOneOfStatement;
import it.auties.protobuf.parser.type.ProtobufObjectType;
import it.auties.protobuf.parser.type.ProtobufTypeReference;

import java.nio.file.Path;
import java.util.*;

import static com.github.javaparser.StaticJavaParser.*;

final class MessageSchemaCreator extends BaseProtobufSchemaCreator<ProtobufMessageStatement> {
    private final List<FieldDeclaration> fields;
    private final List<BodyDeclaration<?>> constructors;
    private final List<MethodDeclaration> methods;
    private final List<TypeDeclaration<?>> members;
    private final boolean mutable;
    MessageSchemaCreator(String packageName, ProtobufMessageStatement protoStatement, boolean mutable, boolean nullable, List<CompilationUnit> classPool, Path output) {
        super(packageName, protoStatement, nullable, classPool, output);
        this.fields = new ArrayList<>();
        this.constructors = new ArrayList<>();
        this.methods = new ArrayList<>();
        this.members = new ArrayList<>();
        this.mutable = mutable;
    }

    @Override
    CompilationUnit generate() {
        var compilationUnit = createCompilationUnit(false);
        if(compilationUnit.existing()){
            return compilationUnit.compilationUnit();
        }

        var fresh = compilationUnit.compilationUnit();
        fresh.addType(generate(fresh));
        return fresh;
    }

    @Override
    TypeDeclaration<?> generate(Node parent) {
        if(mutable){
            var ctClass = new ClassOrInterfaceDeclaration(NodeList.nodeList(Modifier.publicModifier()), false, protoStatement.name());
            allMembers.add(ctClass);
            ctClass.setParentNode(parent);
            if(!(parent instanceof CompilationUnit)) {
                ctClass.setStatic(true);
            }

            addNameAnnotation(ctClass);
            addImplementedType(ProtobufMessage.class.getSimpleName(), ctClass);
            getDeferredImplementations().forEach(entry -> {
                addImplementedType(entry, ctClass);
                ctClass.setFinal(true);
            });
            addClassMembers(ctClass);
            addAllArgsConstructor(ctClass);
            addReservedAnnotation(ctClass);
            fields.forEach(ctClass::addMember);
            constructors.forEach(ctClass::addMember);
            methods.forEach(ctClass::addMember);
            members.forEach(ctClass::addMember);
            return ctClass;
        }

        var ctRecord = new RecordDeclaration(NodeList.nodeList(Modifier.publicModifier()), protoStatement.name());
        allMembers.add(ctRecord);
        ctRecord.setParentNode(parent);
        addNameAnnotation(ctRecord);
        addImplementedType(ProtobufMessage.class.getSimpleName(), ctRecord);
        getDeferredImplementations().forEach(entry -> addImplementedType(entry, ctRecord));
        addRecordMembers(ctRecord);
        addReservedAnnotation(ctRecord);
        members.forEach(ctRecord::addMember);
        constructors.forEach(ctRecord::addMember);
        methods.forEach(ctRecord::addMember);
        members.forEach(ctRecord::addMember);
        return ctRecord;
    }

    private void addAllArgsConstructor(ClassOrInterfaceDeclaration ctClass) {
        var constructorBody = new BlockStmt();
        var parameters = new ArrayList<Parameter>();
        for (var field : fields) {
            var property = field.getAnnotationByClass(ProtobufProperty.class);
            if (property.isEmpty() || !(property.get() instanceof NormalAnnotationExpr annotationExpr)) {
                continue;
            }

            var required = isRequired(annotationExpr);
            for (var variable : field.getVariables()) {
                var variableName = variable.getNameAsString();
                var parameter = new Parameter();
                parameter.setType(variable.getType());
                parameter.setName(variableName);
                parameters.add(parameter);
                var assignment = new AssignExpr();
                var fieldAccess = new FieldAccessExpr(new ThisExpr(), variableName);
                assignment.setTarget(fieldAccess);
                assignment.setOperator(Operator.ASSIGN);
                var value = getClassAssignmentValue(variable, required);
                assignment.setValue(value);
                constructorBody.addStatement(assignment);
            }
        }

        if(hasConstructor(ctClass, parameters)) {
            return;
        }

        var constructor = new ConstructorDeclaration();
        constructors.add(constructor);
        constructor.setPublic(true);
        constructor.setName(ctClass.getName());
        constructor.setParameters(NodeList.nodeList(parameters));
        constructor.setBody(constructorBody);
    }

    private boolean hasConstructor(ClassOrInterfaceDeclaration ctClass, List<Parameter> parameters) {
        var paramTypes = parameters.stream()
                .map(NodeWithType::getTypeAsString)
                .toArray(String[]::new);
        return ctClass.getConstructorByParameterTypes(paramTypes)
                .isPresent();
    }

    private void createSetter(ClassOrInterfaceDeclaration ctClass, ProtobufFieldStatement statement, MessageType type) {
        var setterName = "set" + statement.name().substring(0, 1).toUpperCase(Locale.ROOT) + statement.name().substring(1);
        if (getMethod(ctClass, setterName).isPresent()) {
            return;
        }

        var setter = new MethodDeclaration();
        setter.setPublic(true);
        setter.setName(setterName);
        setter.setType(ctClass.getNameAsString());
        setter.addParameter(new Parameter(parseType(type.value().fieldType()), statement.name()));
        var blockStmt = new BlockStmt();
        setter.setBody(blockStmt);
        var assignment = new AssignExpr();
        var fieldAccess = new FieldAccessExpr(new ThisExpr(), statement.name());
        assignment.setTarget(fieldAccess);
        assignment.setOperator(Operator.ASSIGN);
        assignment.setValue(new NameExpr(statement.name()));
        blockStmt.addStatement(assignment);
        methods.add(setter);
        blockStmt.addStatement(new ReturnStmt(new ThisExpr()));
    }

    private void createGetter(ClassOrInterfaceDeclaration ctClass, ProtobufFieldStatement statement, MessageType type) {
        if (getMethod(ctClass, statement.name()).isPresent()) {
            return;
        }

        var getter = new MethodDeclaration();
        getter.setPublic(true);
        getter.setName(statement.name());
        getter.setType(type.value().accessorType());
        var blockStmt = new BlockStmt();
        getter.setBody(blockStmt);
        var fieldExpression = new NameExpr(statement.name());
        if(statement.required() || statement.repeated() || nullable) {
            blockStmt.addStatement(new ReturnStmt(fieldExpression));
        } else {
            var fieldAccess = new FieldAccessExpr(new ThisExpr(), statement.name());
            var bodyStatement = switch (statement.type().protobufType()) {
                case DOUBLE -> {
                    var conditional = new ConditionalExpr();
                    var nullCheck = new BinaryExpr(fieldAccess, new NullLiteralExpr(), BinaryExpr.Operator.EQUALS);
                    conditional.setCondition(nullCheck);
                    conditional.setThenExpr(new MethodCallExpr(new NameExpr(OptionalDouble.class.getSimpleName()), "empty"));
                    conditional.setElseExpr(new MethodCallExpr(new NameExpr(OptionalDouble.class.getSimpleName()), "of", NodeList.nodeList(fieldExpression)));
                    yield conditional;
                }
                case INT32, SINT32, UINT32, FIXED32, SFIXED32 -> {
                    var conditional = new ConditionalExpr();
                    var nullCheck = new BinaryExpr(fieldAccess, new NullLiteralExpr(), BinaryExpr.Operator.EQUALS);
                    conditional.setCondition(nullCheck);
                    conditional.setThenExpr(new MethodCallExpr(new NameExpr(OptionalInt.class.getSimpleName()), "empty"));
                    conditional.setElseExpr(new MethodCallExpr(new NameExpr(OptionalInt.class.getSimpleName()), "of", NodeList.nodeList(fieldExpression)));
                    yield conditional;
                }
                case INT64, SINT64, UINT64, FIXED64, SFIXED64 -> {
                    var conditional = new ConditionalExpr();
                    var nullCheck = new BinaryExpr(fieldAccess, new NullLiteralExpr(), BinaryExpr.Operator.EQUALS);
                    conditional.setCondition(nullCheck);
                    conditional.setThenExpr(new MethodCallExpr(new NameExpr(OptionalLong.class.getSimpleName()), "empty"));
                    conditional.setElseExpr(new MethodCallExpr(new NameExpr(OptionalLong.class.getSimpleName()), "of", NodeList.nodeList(fieldExpression)));
                    yield conditional;
                }
                default -> new MethodCallExpr(new NameExpr(Optional.class.getSimpleName()), "ofNullable", NodeList.nodeList(fieldExpression));
            };
            blockStmt.addStatement(new ReturnStmt(bodyStatement));
        }
        methods.add(getter);
    }

    private Expression getClassAssignmentValue(VariableDeclarator variable, boolean required) {
        var selectFieldExpression = new NameExpr(variable.getNameAsString());
        if(!required) {
            return selectFieldExpression;
        }

        var objectsExpression = new NameExpr(Objects.class.getSimpleName());
        var missingFieldErrorExpression = new StringLiteralExpr("Missing mandatory field: " + variable.getNameAsString());
        return new MethodCallExpr(objectsExpression, "requireNonNull", NodeList.nodeList(selectFieldExpression, missingFieldErrorExpression));
    }

    private boolean isRequired(NormalAnnotationExpr annotationExpr) {
        return annotationExpr.getPairs()
                .stream()
                .filter(entry -> Objects.equals(entry.getNameAsString(), "required"))
                .findFirst()
                .map(entry -> (BooleanLiteralExpr) entry.getValue())
                .map(BooleanLiteralExpr::getValue)
                .orElse(false);
    }

    private void addClassMembers(ClassOrInterfaceDeclaration ctClass) {
        for(var statement : protoStatement.statements()){
            if(statement instanceof ProtobufFieldStatement fieldStatement){
                addClassField(fieldStatement, ctClass, false);
            }else if(statement instanceof ProtobufMessageStatement messageStatement){
                addNestedMessage(ctClass, messageStatement);
            }else if(statement instanceof ProtobufEnumStatement enumStatement){
                addNestedEnum(ctClass, enumStatement);
            }else if (statement instanceof ProtobufOneOfStatement oneOfStatement){
                addOneOfStatement(ctClass, oneOfStatement);
            }
        }
    }

    private MessageType addClassField(ProtobufFieldStatement fieldStatement, ClassOrInterfaceDeclaration ctClass, boolean wrapType) {
        var parameterType = getMessageType(ctClass, fieldStatement, wrapType);
        var existing = getClassField(fieldStatement, ctClass);
        if(existing.isPresent()){
            if(mutable) {
                createGetter(ctClass, fieldStatement, parameterType);
                createSetter(ctClass, fieldStatement, parameterType);
            }

            return parameterType;
        }

        var field = new FieldDeclaration();
        field.setPublic(true);
        var variable = new VariableDeclarator();
        variable.setType(parameterType.value().fieldType());
        variable.setName(fieldStatement.name());
        field.addVariable(variable);
        var annotation = new NormalAnnotationExpr();
        annotation.setName(ProtobufProperty.class.getSimpleName());
        annotation.addPair("index", new IntegerLiteralExpr(String.valueOf(fieldStatement.index())));
        annotation.addPair("type", new NameExpr(fieldStatement.type().protobufType().name()));
        if(fieldStatement.required()) {
            annotation.addPair("required", new BooleanLiteralExpr(true));
        }
        if(fieldStatement.repeated()) {
            annotation.addPair("repeated", new BooleanLiteralExpr(true));
        }
        field.addAnnotation(annotation);
        fields.add(field);
        createGetter(ctClass, fieldStatement, parameterType);
        createSetter(ctClass, fieldStatement, parameterType);
        return parameterType;
    }

    private Optional<FieldDeclaration> getClassField(ProtobufFieldStatement fieldStatement, ClassOrInterfaceDeclaration ctClass) {
        return ctClass.getFields()
                .stream()
                .filter(entry -> hasIndexField(fieldStatement, entry))
                .findFirst();
    }

    private void addRecordMembers(RecordDeclaration ctRecord) {
        for(var statement : protoStatement.statements()){
            if(statement instanceof ProtobufFieldStatement fieldStatement){
                addRecordParameter(fieldStatement, ctRecord, false);
            }else if(statement instanceof ProtobufMessageStatement messageStatement){
                addNestedMessage(ctRecord, messageStatement);
            }else if(statement instanceof ProtobufEnumStatement enumStatement){
                addNestedEnum(ctRecord, enumStatement);
            }else if (statement instanceof ProtobufOneOfStatement oneOfStatement){
                addOneOfStatement(ctRecord, oneOfStatement);
            }
        }
    }

    private MessageType addRecordParameter(ProtobufFieldStatement fieldStatement, RecordDeclaration ctRecord, boolean wrapType) {
        var parameterType = getMessageType(ctRecord, fieldStatement, wrapType);
        var existing = getRecordParameter(fieldStatement, ctRecord);
        if(existing.isPresent()){
            return parameterType;
        }

        var parameter = new Parameter();
        parameter.setName(fieldStatement.name());
        parameter.setType(parameterType.value().fieldType());
        var annotation = new NormalAnnotationExpr();
        annotation.setName(ProtobufProperty.class.getSimpleName());
        annotation.addPair("index", new IntegerLiteralExpr(String.valueOf(fieldStatement.index())));
        annotation.addPair("type", new NameExpr(fieldStatement.type().protobufType().name()));
        if(fieldStatement.required()) {
            annotation.addPair("required", new BooleanLiteralExpr(true));
            var compactConstructor = getOrCreateCompactConstructor(ctRecord);
            var objectsExpression = new NameExpr(Objects.class.getSimpleName());
            var missingFieldErrorExpression = new StringLiteralExpr("Missing mandatory field: " + fieldStatement.name());
            var selectFieldExpression = new NameExpr(fieldStatement.name());
            var nullCheck = new MethodCallExpr(objectsExpression, "requireNonNull", NodeList.nodeList(selectFieldExpression, missingFieldErrorExpression));
            compactConstructor.getBody().addStatement(nullCheck);
        }
        if(fieldStatement.repeated()) {
            annotation.addPair("repeated", new BooleanLiteralExpr(true));
        }
        parameter.addAnnotation(annotation);
        ctRecord.addParameter(parameter);
        return parameterType;
    }

    private Optional<Parameter> getRecordParameter(ProtobufFieldStatement fieldStatement, RecordDeclaration ctRecord) {
        return ctRecord.getParameters()
                .stream()
                .filter(entry -> hasIndexField(fieldStatement, entry))
                .findFirst();
    }

    private boolean hasIndexField(ProtobufFieldStatement fieldStatement, NodeWithAnnotations<?> entry) {
        var annotation = entry.getAnnotationByClass(ProtobufProperty.class);
        if(annotation.isEmpty()){
            return false;
        }

        if(!(annotation.get() instanceof NormalAnnotationExpr annotationExpr)){
            return false;
        }

        return annotationExpr.getPairs()
                .stream()
                .filter(arg -> Objects.equals(arg.getNameAsString(), "index"))
                .filter(arg -> arg.getValue() instanceof IntegerLiteralExpr)
                .map(arg -> (IntegerLiteralExpr) arg.getValue())
                .anyMatch(index -> index.asNumber().intValue() == fieldStatement.index());
    }

    private MessageType getMessageType(TypeDeclaration<?> scope, ProtobufFieldStatement fieldStatement, boolean wrapType) {
        var qualifiedType = getMessageFieldType(fieldStatement.type(), fieldStatement.required(), fieldStatement.repeated());
        var typeParameter = fieldStatement.repeated() ? qualifiedType : null;
        var javaType = typeParameter == null ? qualifiedType : MessageFieldType.listType(typeParameter);
        if (!wrapType) {
            return new MessageType(javaType, null, null);
        }

        if (fieldStatement.repeated() || fieldStatement.type().protobufType() != ProtobufType.OBJECT) {
            var wrapperQualifiedName = fieldStatement.name().substring(0, 1).toUpperCase() + fieldStatement.name().substring(1);
            var wrapperRecord = createWrapperRecord(scope, wrapperQualifiedName, parseType(javaType.fieldType()));
            var accessorType = "%s<%s>".formatted(Optional.class.getSimpleName(), wrapperQualifiedName);
            var fieldType = mutable ? wrapperQualifiedName : accessorType;
            return new MessageType(new MessageFieldType(fieldType, accessorType), wrapperRecord, null);
        }

        var fieldType = (ProtobufObjectType) fieldStatement.type();
        var wrapperQuery = getTypeDeclaration(fieldType.declaration().qualifiedCanonicalName(), QueryType.ANY);
        wrapperQuery.ifPresent(queryResult -> queryResult.result().addModifier(Keyword.FINAL));
        var recordType = wrapperQuery.map(QueryResult::result)
                .map(entry -> entry.getFullyQualifiedName().orElseThrow())
                .or(() -> Optional.of(fieldType.name()))
                .get();
        var messageAccessorType = "%s<%s>".formatted(Optional.class.getSimpleName(), recordType);
        var messageFieldType = mutable ? recordType : messageAccessorType;
        return new MessageType(new MessageFieldType(messageFieldType, messageAccessorType), wrapperQuery.map(QueryResult::result).orElse(null), wrapperQuery.isPresent() ? null : fieldType.name());
    }

    private RecordDeclaration createWrapperRecord(TypeDeclaration<?> scope, String wrapperSimpleName, Type javaType) {
        var known = getTypeMember(scope, wrapperSimpleName);
        if(known.isPresent()) {
            return (RecordDeclaration) known.get();
        }

        var wrapperRecord = new RecordDeclaration(NodeList.nodeList(Modifier.publicModifier()), wrapperSimpleName);
        var parameter = new Parameter(javaType, "value");
        wrapperRecord.addParameter(parameter);
        wrapperRecord.setParentNode(scope);
        members.add(wrapperRecord);
        allMembers.add(wrapperRecord);
        return wrapperRecord;
    }

    private MessageFieldType getMessageFieldType(ProtobufTypeReference type, boolean required, boolean repeated) {
        if (!(type instanceof ProtobufObjectType messageType)) {
            var fieldType = getJavaType(type, required, repeated, mutable);
            var accessorType = getJavaType(type, required, repeated, repeated);
            return new MessageFieldType(fieldType, accessorType);
        }

        var objectType = messageType.declaration()
                .qualifiedCanonicalNameWithoutPackage();
        var accessorType = nullable || required ? objectType : "%s<%s>".formatted(Optional.class.getSimpleName(), objectType);
        var fieldType = mutable ? objectType : accessorType;
        return new MessageFieldType(fieldType, accessorType);
    }

    private String getJavaType(ProtobufTypeReference type, boolean required, boolean repeated, boolean forceNullable) {
        if (!repeated && required) {
            return type.protobufType()
                    .primitiveType()
                    .getSimpleName();
        }

        if(forceNullable || nullable) {
            return type.protobufType() == ProtobufType.BYTES ? "byte[]" : type.protobufType()
                    .wrappedType()
                    .getSimpleName();
        }

        return switch (type.protobufType()) {
            case DOUBLE -> OptionalDouble.class.getSimpleName();
            case BOOL -> "%s<Boolean>".formatted(Optional.class.getSimpleName());
            case FLOAT -> "%s<Float>".formatted(Optional.class.getSimpleName());
            case STRING ->"%s<String>".formatted(Optional.class.getSimpleName());
            case BYTES -> "%s<byte[]>".formatted(Optional.class.getSimpleName());
            case INT32, SINT32, UINT32, FIXED32, SFIXED32 -> OptionalInt.class.getSimpleName();
            case INT64, SINT64, UINT64, FIXED64, SFIXED64 -> OptionalLong.class.getSimpleName();
            default -> throw new IllegalStateException("Unexpected value: " + type.protobufType());
        };
    }

    private record MessageType(MessageFieldType value, TypeDeclaration<?> wrapper, String fallbackWrapper) {
        public boolean hasWrapper(){
            return wrapper != null;
        }

        public boolean hasFallbackWrapper(){
            return fallbackWrapper != null;
        }
    }

    private record MessageFieldType(String fieldType, String accessorType) {
        public static MessageFieldType listType(MessageFieldType typeParameter) {
            var type = "%s<%s>".formatted(List.class.getSimpleName(), typeParameter.fieldType());
            return new MessageFieldType(type, type);
        }
    }

    private CompactConstructorDeclaration getOrCreateCompactConstructor(RecordDeclaration ctRecord) {
        var compactConstructors = ctRecord.getCompactConstructors();
        if (!compactConstructors.isEmpty()) {
            return compactConstructors.get(0);
        }

        var compactConstructor = new CompactConstructorDeclaration(NodeList.nodeList(Modifier.publicModifier()), protoStatement.name());
        constructors.add(compactConstructor);
        return compactConstructor;
    }

    private void addNestedMessage(TypeDeclaration<?> ctRecord, ProtobufMessageStatement messageStatement) {
        var result = getTypeMember(ctRecord, messageStatement.name());
        if (result.isPresent()) {
            return;
        }

        var creator = new MessageSchemaCreator(packageName, messageStatement, mutable, nullable, classPool, output);
        var member = creator.generate(ctRecord);
        members.add(member);
        allMembers.add(member);
    }

    private void addNestedEnum(TypeDeclaration<?> ctRecord, ProtobufEnumStatement enumStatement) {
        var result = getTypeMember(ctRecord, enumStatement.name());
        if (result.isPresent()) {
            return;
        }
        
        var creator = new EnumSchemaCreator(packageName, enumStatement, classPool, output);
        var member = creator.generate(ctRecord);
        members.add(member);
        allMembers.add(member);
    }

    private void addOneOfStatement(TypeDeclaration<?> typeDeclaration, ProtobufOneOfStatement oneOfStatement) {
        var ctInterface = createOneOfInterface(typeDeclaration, oneOfStatement);
        var ctMethod = createOneOfMethod(typeDeclaration, oneOfStatement, ctInterface);
        var ctMethodBody = new BlockStmt();
        ctMethod.setBody(ctMethodBody);
        var permittedTypes = new NodeList<ClassOrInterfaceType>();
        var index = 0;
        for (var oneOfFieldStatement : oneOfStatement.statements()) {
            if(index++ != oneOfStatement.statements().size() - 1) {
                var conditional = new IfStmt();
                var fieldAccess = new FieldAccessExpr(new ThisExpr(), oneOfFieldStatement.name());
                if(mutable || nullable) {
                    var nullCheck = new BinaryExpr(fieldAccess, new NullLiteralExpr(), BinaryExpr.Operator.NOT_EQUALS);
                    conditional.setCondition(nullCheck);
                }else {
                    var isPresentCheck = new MethodCallExpr(fieldAccess, "isPresent");
                    conditional.setCondition(isPresentCheck);
                }

                var thenBlock = new BlockStmt();
                if(mutable && !nullable) {
                    thenBlock.addStatement(new ReturnStmt(new MethodCallExpr(new NameExpr(Optional.class.getSimpleName()), "of", NodeList.nodeList(fieldAccess))));
                }else {
                    thenBlock.addStatement(new ReturnStmt(fieldAccess));
                }
                conditional.setThenStmt(thenBlock);
                ctMethodBody.addStatement(conditional);
            }else {
                var fieldAccess = new FieldAccessExpr(new ThisExpr(), oneOfFieldStatement.name());
                if(mutable && !nullable) {
                    ctMethodBody.addStatement(new ReturnStmt(new MethodCallExpr(new NameExpr(Optional.class.getSimpleName()), "of", NodeList.nodeList(fieldAccess))));
                }else {
                    ctMethodBody.addStatement(new ReturnStmt(fieldAccess));
                }
            }

            if(typeDeclaration instanceof RecordDeclaration ctRecord){
                var result = addRecordParameter(oneOfFieldStatement, ctRecord, true);
                var type = addOneOfInterface(result, ctInterface);
                permittedTypes.add(type);
            }else if(typeDeclaration instanceof ClassOrInterfaceDeclaration ctClass){
                var result = addClassField(oneOfFieldStatement, ctClass, true);
                var type = addOneOfInterface(result, ctInterface);
                permittedTypes.add(type);
            }else {
                throw new IllegalArgumentException("Unknown value: " + typeDeclaration.getClass().getName());
            }
        }

        if(index == 0) {
            ctMethodBody.addStatement(new ReturnStmt(new NullLiteralExpr()));
        }

        ctInterface.setPermittedTypes(permittedTypes);
    }

    private MethodDeclaration createOneOfMethod(TypeDeclaration<?> typeDeclaration, ProtobufOneOfStatement oneOfStatement, ClassOrInterfaceDeclaration ctInterface) {
        var existing = getMethod(typeDeclaration, oneOfStatement.name());
        if(existing.isPresent()) {
            return existing.get();
        }

        var ctMethod = new MethodDeclaration();
        ctMethod.setPublic(true);
        ctMethod.setName(oneOfStatement.name());
        ctMethod.setType(nullable ? ctInterface.getNameAsString() : "%s<? extends %s>".formatted(Optional.class.getSimpleName(), ctInterface.getNameAsString()));
        methods.add(ctMethod);
        return ctMethod;
    }

    private ClassOrInterfaceDeclaration createOneOfInterface(TypeDeclaration<?> scope, ProtobufOneOfStatement oneOfStatement) {
        var result = getTypeMember(scope, oneOfStatement.className());
        if (result.isPresent()) {
            return (ClassOrInterfaceDeclaration) result.get();
        }

        var ctInterface = new ClassOrInterfaceDeclaration();
        ctInterface.setPublic(true);
        ctInterface.addModifier(Keyword.SEALED);
        ctInterface.setInterface(true);
        ctInterface.setName(oneOfStatement.className());
        ctInterface.setParentNode(scope);
        members.add(ctInterface);
        allMembers.add(ctInterface);
        return ctInterface;
    }

    private ClassOrInterfaceType addOneOfInterface(MessageType result, ClassOrInterfaceDeclaration ctInterface) {
        if (result.hasWrapper()) {
            addImplementedType(ctInterface, result.wrapper());
            return parseClassOrInterfaceType(result.wrapper().getFullyQualifiedName().orElseThrow());
        } else if (result.hasFallbackWrapper()) {
            addOneOfDeferredImplementation(result.fallbackWrapper(), ctInterface);
            return parseClassOrInterfaceType(result.fallbackWrapper());
        } else {
            throw new RuntimeException("Invalid wrapper for one of statement");
        }
    }

    @Override
    Optional<CompilationUnit> update(String name) {
        var result = getTypeDeclaration(name, QueryType.MESSAGE);
        if (result.isEmpty()) {
            return Optional.of(generate());
        }

        if(result.get().result() instanceof RecordDeclaration ctRecord){
            getDeferredImplementations().forEach(entry -> addImplementedType(entry, ctRecord));
            addRecordMembers(ctRecord);
            addReservedAnnotation(ctRecord);
            return Optional.of(result.get().compilationUnit());
        }

        var ctClass = (ClassOrInterfaceDeclaration) result.get().result();
        getDeferredImplementations().forEach(entry -> {
            addImplementedType(entry, ctClass);
            ctClass.setFinal(true);
        });
        addClassMembers(ctClass);
        addAllArgsConstructor(ctClass);
        addReservedAnnotation(ctClass);
        return Optional.of(result.get().compilationUnit());
    }
}

package it.auties.protobuf.schema.schema;

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
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.parser.tree.body.ProtobufBodyTree;
import it.auties.protobuf.parser.tree.body.object.ProtobufEnumTree;
import it.auties.protobuf.parser.tree.body.object.ProtobufMessageTree;
import it.auties.protobuf.parser.tree.body.oneof.ProtobufOneofTree;
import it.auties.protobuf.parser.tree.nested.field.ProtobufFieldTree;
import it.auties.protobuf.parser.tree.nested.field.ProtobufGroupableFieldTree;
import it.auties.protobuf.parser.type.ProtobufObjectType;
import it.auties.protobuf.parser.type.ProtobufTypeReference;
import it.auties.protobuf.schema.util.AstUtils;

import java.nio.file.Path;
import java.util.*;

import static com.github.javaparser.StaticJavaParser.parseClassOrInterfaceType;
import static com.github.javaparser.StaticJavaParser.parseType;
import static it.auties.protobuf.parser.tree.nested.field.ProtobufFieldTree.Modifier.Type.REPEATED;
import static it.auties.protobuf.parser.tree.nested.field.ProtobufFieldTree.Modifier.Type.REQUIRED;

final class MessageSchemaCreator extends BaseProtobufSchemaCreator<ProtobufMessageTree> {
    private final List<FieldDeclaration> fields;
    private final List<BodyDeclaration<?>> constructors;
    private final List<MethodDeclaration> methods;
    private final List<TypeDeclaration<?>> members;
    private final boolean mutable;
    MessageSchemaCreator(String packageName, ProtobufMessageTree protoStatement, boolean mutable, boolean nullable, List<CompilationUnit> classPool, Path output) {
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
            var ctClass = new ClassOrInterfaceDeclaration(NodeList.nodeList(Modifier.publicModifier()), false, AstUtils.toJavaName(protoStatement.name().orElseThrow()));
            allMembers.add(ctClass);
            ctClass.setParentNode(parent);
            if(!(parent instanceof CompilationUnit)) {
                ctClass.setStatic(true);
            }

            addMessageName(ctClass);
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

        var ctRecord = new RecordDeclaration(NodeList.nodeList(Modifier.publicModifier()), AstUtils.toJavaName(protoStatement.name().orElseThrow()));
        allMembers.add(ctRecord);
        ctRecord.setParentNode(parent);
        addMessageName(ctRecord);
        getDeferredImplementations().forEach(entry -> addImplementedType(entry, ctRecord));
        addRecordMembers(ctRecord);
        addReservedAnnotation(ctRecord);
        constructors.forEach(ctRecord::addMember);
        methods.forEach(ctRecord::addMember);
        members.forEach(ctRecord::addMember);
        return ctRecord;
    }

    private void addMessageName(NodeWithAnnotations<?> node) {
        var annotation = getOrAddAnnotation(node, ProtobufMessage.class);
        annotation.setPairs(NodeList.nodeList(new MemberValuePair("name", new StringLiteralExpr(protoStatement.qualifiedCanonicalName().orElseThrow()))));
        node.addAnnotation(annotation);
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

    private void createSetter(ClassOrInterfaceDeclaration ctClass, ProtobufFieldTree statement, MessageType type) {
        var statementName = statement.name().orElseThrow();
        var setterName = "set" + statementName.substring(0, 1).toUpperCase(Locale.ROOT) + statementName.substring(1);
        if (getMethod(ctClass, setterName).isPresent()) {
            return;
        }

        var javaStatementName = AstUtils.toJavaName(statementName);
        var setter = new MethodDeclaration();
        setter.setPublic(true);
        setter.setName(setterName);
        setter.setType(AstUtils.toCanonicalJavaName(ctClass.getNameAsString()));
        setter.addParameter(new Parameter(parseType(AstUtils.toCanonicalJavaName(type.value().fieldType())), javaStatementName));
        var blockStmt = new BlockStmt();
        setter.setBody(blockStmt);
        var assignment = new AssignExpr();
        var fieldAccess = new FieldAccessExpr(new ThisExpr(), javaStatementName);
        assignment.setTarget(fieldAccess);
        assignment.setOperator(Operator.ASSIGN);
        assignment.setValue(new NameExpr(javaStatementName));
        blockStmt.addStatement(assignment);
        methods.add(setter);
        blockStmt.addStatement(new ReturnStmt(new ThisExpr()));
    }

    private void createGetter(ClassOrInterfaceDeclaration ctClass, ProtobufGroupableFieldTree statement, MessageType type) {
        if (getMethod(ctClass, AstUtils.toJavaName(statement.name().orElseThrow())).isPresent()) {
            return;
        }

        var getter = new MethodDeclaration();
        getter.setPublic(true);
        getter.setName(AstUtils.toJavaName(statement.name().orElseThrow()));
        getter.setType(AstUtils.toCanonicalJavaName(type.value().accessorType()));
        var blockStmt = new BlockStmt();
        getter.setBody(blockStmt);
        var fieldExpression = new NameExpr(AstUtils.toJavaName(statement.name().orElseThrow()));
        var modifier = statement.modifier().orElse(null);
        if((modifier != null && (modifier.type() == REQUIRED || modifier.type() == ProtobufFieldTree.Modifier.Type.REPEATED)) || nullable) {
            blockStmt.addStatement(new ReturnStmt(fieldExpression));
        } else {
            var fieldAccess = new FieldAccessExpr(new ThisExpr(), AstUtils.toJavaName(statement.name().orElseThrow()));
            var bodyStatement = switch (statement.type().orElseThrow().protobufType()) {
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
            if(statement instanceof ProtobufGroupableFieldTree fieldStatement){
                addClassField(fieldStatement, ctClass, false);
            }else if(statement instanceof ProtobufMessageTree messageStatement){
                addNestedMessage(ctClass, messageStatement);
            }else if(statement instanceof ProtobufEnumTree enumStatement){
                addNestedEnum(ctClass, enumStatement);
            }else if (statement instanceof ProtobufOneofTree oneOfStatement){
                addOneOfStatement(ctClass, oneOfStatement);
            }
        }
    }

    private MessageType addClassField(ProtobufGroupableFieldTree fieldStatement, ClassOrInterfaceDeclaration ctClass, boolean wrapType) {
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
        variable.setType(AstUtils.toCanonicalJavaName(parameterType.value().fieldType()));
        variable.setName(AstUtils.toJavaName(fieldStatement.name().orElseThrow()));
        field.addVariable(variable);
        var annotation = new NormalAnnotationExpr();
        annotation.setName(ProtobufProperty.class.getSimpleName());
        annotation.addPair("index", new IntegerLiteralExpr(String.valueOf(fieldStatement.index().orElseThrow())));
        annotation.addPair("type", new NameExpr(fieldStatement.type().orElseThrow().protobufType().name()));
        var modifier = fieldStatement.modifier().orElse(null);
        if(modifier != null && modifier.type() == REQUIRED) {
            annotation.addPair("required", new BooleanLiteralExpr(true));
        }
        field.addAnnotation(annotation);
        fields.add(field);
        createGetter(ctClass, fieldStatement, parameterType);
        createSetter(ctClass, fieldStatement, parameterType);
        return parameterType;
    }

    private Optional<FieldDeclaration> getClassField(ProtobufFieldTree fieldStatement, ClassOrInterfaceDeclaration ctClass) {
        return ctClass.getFields()
                .stream()
                .filter(entry -> hasIndexField(fieldStatement, entry))
                .findFirst();
    }

    private void addRecordMembers(RecordDeclaration ctRecord) {
        for(var statement : protoStatement.statements()){
            if(statement instanceof ProtobufGroupableFieldTree fieldStatement){
                addRecordParameter(fieldStatement, ctRecord, false);
            }else if(statement instanceof ProtobufMessageTree messageStatement){
                addNestedMessage(ctRecord, messageStatement);
            }else if(statement instanceof ProtobufEnumTree enumStatement){
                addNestedEnum(ctRecord, enumStatement);
            }else if (statement instanceof ProtobufOneofTree oneOfStatement){
                addOneOfStatement(ctRecord, oneOfStatement);
            }
        }
    }

    private MessageType addRecordParameter(ProtobufGroupableFieldTree fieldStatement, RecordDeclaration ctRecord, boolean wrapType) {
        var parameterType = getMessageType(ctRecord, fieldStatement, wrapType);
        var existing = getRecordParameter(fieldStatement, ctRecord);
        if(existing.isPresent()){
            return parameterType;
        }

        var parameter = new Parameter();
        parameter.setName(AstUtils.toJavaName(fieldStatement.name().orElseThrow()));
        parameter.setType(AstUtils.toCanonicalJavaName(parameterType.value().fieldType()));
        var annotation = new NormalAnnotationExpr();
        annotation.setName(ProtobufProperty.class.getSimpleName());
        annotation.addPair("index", new IntegerLiteralExpr(String.valueOf(fieldStatement.index().orElseThrow())));
        annotation.addPair("type", new NameExpr(fieldStatement.type().orElseThrow().protobufType().name()));
        if(fieldStatement.modifier().orElseThrow().type() == REQUIRED) {
            annotation.addPair("required", new BooleanLiteralExpr(true));
            var compactConstructor = getOrCreateCompactConstructor(ctRecord);
            var objectsExpression = new NameExpr(Objects.class.getSimpleName());
            var missingFieldErrorExpression = new StringLiteralExpr("Missing mandatory field: " + AstUtils.toJavaName(fieldStatement.name().orElseThrow()));
            var selectFieldExpression = new NameExpr(AstUtils.toJavaName(fieldStatement.name().orElseThrow()));
            var nullCheck = new MethodCallExpr(objectsExpression, "requireNonNull", NodeList.nodeList(selectFieldExpression, missingFieldErrorExpression));
            compactConstructor.getBody().addStatement(nullCheck);
        }
        parameter.addAnnotation(annotation);
        ctRecord.addParameter(parameter);
        return parameterType;
    }

    private Optional<Parameter> getRecordParameter(ProtobufFieldTree fieldStatement, RecordDeclaration ctRecord) {
        return ctRecord.getParameters()
                .stream()
                .filter(entry -> hasIndexField(fieldStatement, entry))
                .findFirst();
    }

    private boolean hasIndexField(ProtobufFieldTree fieldStatement, NodeWithAnnotations<?> entry) {
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
                .anyMatch(index -> index.asNumber().intValue() == fieldStatement.index().orElseThrow());
    }

    private MessageType getMessageType(TypeDeclaration<?> scope, ProtobufGroupableFieldTree fieldStatement, boolean wrapType) {
        var modifier = fieldStatement instanceof ProtobufGroupableFieldTree fieldTree ? fieldTree.modifier().orElse(null) : null;
        var repeated = modifier != null && modifier.type() == REPEATED;
        var qualifiedType = getMessageFieldType(fieldStatement.type().orElseThrow(), modifier != null && modifier.type() == REQUIRED, repeated);
        var typeParameter = repeated ? qualifiedType : null;
        var javaType = typeParameter == null ? qualifiedType : MessageFieldType.listType(typeParameter);
        if (!wrapType) {
            return new MessageType(javaType, null, null);
        }

        var fieldStatementType = fieldStatement.type().orElseThrow();
        if (repeated || fieldStatementType.protobufType() != ProtobufType.MESSAGE || fieldStatementType.protobufType() != ProtobufType.ENUM) {
            var fieldName = AstUtils.toJavaName(fieldStatement.name().orElseThrow());
            var wrapperQualifiedName = fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
            var wrapperRecord = createWrapperRecord(scope, wrapperQualifiedName, parseType(AstUtils.toCanonicalJavaName(javaType.fieldType())));
            var accessorType = nullable ? wrapperQualifiedName : "%s<%s>".formatted(Optional.class.getSimpleName(), wrapperQualifiedName);
            var fieldType = mutable ? wrapperQualifiedName : accessorType;
            return new MessageType(new MessageFieldType(fieldType, accessorType), wrapperRecord, null);
        }

        var fieldType = (ProtobufObjectType) fieldStatementType;
        var fieldTypeName = fieldType.declaration()
                .flatMap(ProtobufBodyTree::qualifiedCanonicalName)
                .orElseThrow();
        var wrapperQuery = getTypeDeclaration(fieldTypeName, QueryType.ANY);
        wrapperQuery.ifPresent(queryResult -> queryResult.result().addModifier(Keyword.FINAL));
        var recordType = wrapperQuery.map(QueryResult::result)
                .map(entry -> entry.getFullyQualifiedName().orElseThrow())
                .or(() -> Optional.of(fieldType.name()))
                .get();
        var messageAccessorType = nullable ? recordType : "%s<%s>".formatted(Optional.class.getSimpleName(), recordType);
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
                .flatMap(ProtobufBodyTree::qualifiedCanonicalName)
                .orElseThrow();
        var accessorType = nullable || required ? objectType : "%s<%s>".formatted(Optional.class.getSimpleName(), objectType);
        var fieldType = mutable ? objectType : accessorType;
        return new MessageFieldType(fieldType, accessorType);
    }

    private String getJavaType(ProtobufTypeReference type, boolean required, boolean repeated, boolean forceNullable) {
        if (!repeated && required) {
            return type.protobufType()
                    .serializedType()
                    .getSimpleName();
        }

        if(forceNullable || nullable) {
            return type.protobufType() == ProtobufType.BYTES ? "byte[]" : type.protobufType()
                    .serializedWrappedType()
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
        var definedCompactConstructors = ctRecord.getCompactConstructors();
        if (!definedCompactConstructors.isEmpty()) {
            return definedCompactConstructors.getFirst();
        }

        var inProgressCompactConstructor = constructors.stream()
                .filter(BodyDeclaration::isCompactConstructorDeclaration)
                .map(entry -> (CompactConstructorDeclaration) entry)
                .findFirst();
        if(inProgressCompactConstructor.isPresent()) {
            return inProgressCompactConstructor.get();
        }

        var compactConstructor = new CompactConstructorDeclaration(NodeList.nodeList(Modifier.publicModifier()), AstUtils.toJavaName(protoStatement.name().orElseThrow()));
        constructors.add(compactConstructor);
        return compactConstructor;
    }

    private void addNestedMessage(TypeDeclaration<?> ctRecord, ProtobufMessageTree messageStatement) {
        var result = getTypeMember(ctRecord, AstUtils.toJavaName(messageStatement.name().orElseThrow()));
        if (result.isPresent()) {
            return;
        }

        var creator = new MessageSchemaCreator(packageName, messageStatement, mutable, nullable, classPool, output);
        var member = creator.generate(ctRecord);
        members.add(member);
        allMembers.add(member);
    }

    private void addNestedEnum(TypeDeclaration<?> ctRecord, ProtobufEnumTree enumStatement) {
        var result = getTypeMember(ctRecord, AstUtils.toJavaName(enumStatement.name().orElseThrow()));
        if (result.isPresent()) {
            return;
        }

        var creator = new EnumSchemaCreator(packageName, enumStatement, classPool, output);
        var member = creator.generate(ctRecord);
        members.add(member);
        allMembers.add(member);
    }

    private void addOneOfStatement(TypeDeclaration<?> typeDeclaration, ProtobufOneofTree oneOfStatement) {
        var ctInterface = createOneOfInterface(typeDeclaration, oneOfStatement);
        var ctMethod = createOneOfMethod(typeDeclaration, oneOfStatement, ctInterface);
        var ctMethodBody = new BlockStmt();
        ctMethod.setBody(ctMethodBody);
        var permittedTypes = new NodeList<ClassOrInterfaceType>();
        var index = 0;
        for (var oneOfFieldStatement : oneOfStatement.statements()) {
            if(index++ != oneOfStatement.statements().size() - 1) {
                var conditional = new IfStmt();
                var fieldAccess = new FieldAccessExpr(new ThisExpr(), AstUtils.toJavaName(oneOfFieldStatement.name().orElseThrow()));
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
                var fieldAccess = new FieldAccessExpr(new ThisExpr(), AstUtils.toJavaName(oneOfFieldStatement.name().orElseThrow()));
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

    private MethodDeclaration createOneOfMethod(TypeDeclaration<?> typeDeclaration, ProtobufOneofTree oneOfStatement, ClassOrInterfaceDeclaration ctInterface) {
        var existing = getMethod(typeDeclaration, AstUtils.toJavaName(oneOfStatement.name().orElseThrow()));
        if(existing.isPresent()) {
            return existing.get();
        }

        var ctMethod = new MethodDeclaration();
        ctMethod.setPublic(true);
        ctMethod.setName(AstUtils.toJavaName(oneOfStatement.name().orElseThrow()));
        ctMethod.setType(AstUtils.toCanonicalJavaName(nullable ? ctInterface.getNameAsString() : "%s<? extends %s>".formatted(Optional.class.getSimpleName(), ctInterface.getNameAsString())));
        methods.add(ctMethod);
        return ctMethod;
    }

    private ClassOrInterfaceDeclaration createOneOfInterface(TypeDeclaration<?> scope, ProtobufOneofTree oneOfStatement) {
        var result = getTypeMember(scope, oneOfStatement.className().orElseThrow());
        if (result.isPresent()) {
            return (ClassOrInterfaceDeclaration) result.get();
        }

        var ctInterface = new ClassOrInterfaceDeclaration();
        ctInterface.setPublic(true);
        ctInterface.addModifier(Keyword.SEALED);
        ctInterface.setInterface(true);
        ctInterface.setName(oneOfStatement.className().orElseThrow());
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

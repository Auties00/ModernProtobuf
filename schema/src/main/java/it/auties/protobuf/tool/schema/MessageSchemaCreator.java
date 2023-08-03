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
import lombok.NonNull;

import java.nio.file.Path;
import java.util.*;

import static com.github.javaparser.StaticJavaParser.parseClassOrInterfaceType;
import static com.github.javaparser.StaticJavaParser.parseType;

final class MessageSchemaCreator extends SchemaCreator<ProtobufMessageStatement> {
    MessageSchemaCreator(String packageName, @NonNull ProtobufMessageStatement protoStatement, boolean mutable, @NonNull List<CompilationUnit> classPool, Path output) {
        super(packageName, protoStatement, mutable, classPool, output);
    }

    @Override
    CompilationUnit generate() {
        var compilationUnit = createCompilationUnit(false);
        if(compilationUnit.existing()){
            return compilationUnit.compilationUnit();
        }

        var fresh = compilationUnit.compilationUnit();
        generate(fresh);
        return fresh;
    }

    @Override
    void generate(Node parent) {
        if(mutable){
            var ctClass = new ClassOrInterfaceDeclaration(NodeList.nodeList(Modifier.publicModifier()), false, protoStatement.name());
            linkToParent(parent, ctClass);
            addImplementedType(ProtobufMessage.class.getSimpleName(), ctClass);
            getDeferredImplementation(protoStatement.name()).ifPresent(entry -> {
                addImplementedType(entry, ctClass);
                ctClass.setFinal(true);
            });
            addClassMembers(ctClass);
            addAllArgsConstructor(ctClass);
            addReservedAnnotation(ctClass);
            return;
        }

        var ctRecord = new RecordDeclaration(NodeList.nodeList(Modifier.publicModifier()), protoStatement.name());
        linkToParent(parent, ctRecord);
        addImplementedType(ProtobufMessage.class.getSimpleName(), ctRecord);
        getDeferredImplementation(protoStatement.name())
                .ifPresent(entry -> addImplementedType(entry, ctRecord));
        addRecordMembers(ctRecord);
        addReservedAnnotation(ctRecord);
    }

    private void addAllArgsConstructor(ClassOrInterfaceDeclaration ctClass) {
        var constructorBody = new BlockStmt();
        var parameters = new ArrayList<Parameter>();
        for (var field : ctClass.getFields()) {
            var property = field.getAnnotationByClass(ProtobufProperty.class);
            if (property.isEmpty() || !(property.get() instanceof NormalAnnotationExpr annotationExpr)) {
                continue;
            }

            var required = isRequired(annotationExpr);
            for (var variable : field.getVariables()) {
                var variableName = variable.getNameAsString();
                createGetter(ctClass, variable, variableName);
                createSetter(ctClass, variable, variableName);
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
        ctClass.addMember(constructor);
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

    private void createSetter(ClassOrInterfaceDeclaration ctClass, VariableDeclarator variable, String variableName) {
        var setterName = "set" + variableName.substring(0, 1).toUpperCase(Locale.ROOT) + variableName.substring(1);
        if (getMethod(ctClass, setterName).isPresent()) {
            return;
        }

        var setter = ctClass.addMethod(setterName, Keyword.PUBLIC);
        setter.addParameter(new Parameter(variable.getType(), variableName));
        var blockStmt = new BlockStmt();
        setter.setBody(blockStmt);
        var assignment = new AssignExpr();
        var fieldAccess = new FieldAccessExpr(new ThisExpr(), variableName);
        assignment.setTarget(fieldAccess);
        assignment.setOperator(Operator.ASSIGN);
        assignment.setValue(new NameExpr(variableName));
        blockStmt.addStatement(assignment);
    }

    private void createGetter(ClassOrInterfaceDeclaration ctClass, VariableDeclarator variable, String variableName) {
        if (getMethod(ctClass, variableName).isPresent()) {
            return;
        }

        var getter = ctClass.addMethod(variableName, Keyword.PUBLIC);
        getter.setType(variable.getType());
        var blockStmt = new BlockStmt();
        getter.setBody(blockStmt);
        blockStmt.addStatement(new ReturnStmt(variableName));
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
            return parameterType;
        }

        var type = parseType(getQualifiedName(ctClass, parameterType.type().toString()));
        var field = new FieldDeclaration(NodeList.nodeList(Modifier.publicModifier()), type, fieldStatement.name());
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
        ctClass.addMember(field);
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


        var parameter = new Parameter(parseType(getQualifiedName(ctRecord, parameterType.type().toString())), fieldStatement.name());
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
        var qualifiedType = getMessageFieldRawType(scope, !fieldStatement.repeated(), fieldStatement.type());
        var qualifiedFieldType = fieldStatement.repeated() ? List.class.getName() : qualifiedType;
        var typeParameter = fieldStatement.repeated() ? qualifiedType : null;
        var javaType = parseType(typeParameter == null ? qualifiedFieldType : "%s<%s>".formatted(qualifiedFieldType, typeParameter));
        if (!wrapType) {
            return new MessageType(javaType, qualifiedFieldType, typeParameter, null, null);
        }

        if (!fieldStatement.repeated() && fieldStatement.type().protobufType() == ProtobufType.OBJECT) {
            var fieldType = (ProtobufObjectType) fieldStatement.type();
            var wrapperType = getTypeDeclaration(fieldType.name(), QueryType.ANY);
            wrapperType.ifPresent(queryResult -> queryResult.result().addModifier(Keyword.FINAL));
            return new MessageType(javaType, qualifiedFieldType, typeParameter, wrapperType.map(QueryResult::result).orElse(null), wrapperType.isPresent() ? null : fieldType.name());
        }

        var wrapperSimpleName = fieldStatement.name().substring(0, 1).toUpperCase() + fieldStatement.name().substring(1);
        var wrapperRecord = createWrapperRecord(scope, wrapperSimpleName, javaType);
        var wrapperQualifiedName = packageName != null ? packageName + "." + wrapperSimpleName : wrapperSimpleName;
        return new MessageType(parseType(wrapperQualifiedName), qualifiedFieldType, typeParameter, wrapperRecord, null);
    }

    private RecordDeclaration createWrapperRecord(TypeDeclaration<?> scope, String wrapperSimpleName, Type javaType) {
        var known = getTypeMember(scope, wrapperSimpleName);
        if(known.isPresent()) {
            return (RecordDeclaration) known.get();
        }

        var wrapperRecord = new RecordDeclaration(NodeList.nodeList(Modifier.publicModifier()), wrapperSimpleName);
        var parameter = new Parameter(parseType(getQualifiedName(scope, javaType.toString())), "value");
        wrapperRecord.addParameter(parameter);
        scope.addMember(wrapperRecord);
        return wrapperRecord;
    }

    private String getMessageFieldRawType(TypeDeclaration<?> scope, boolean primitive, ProtobufTypeReference type) {
        if (type instanceof ProtobufObjectType messageType) {
            var qualifiedName = messageType.declaration().qualifiedCanonicalName();
            var fullName = packageName != null ? "%s.%s".formatted(packageName, qualifiedName) : qualifiedName;
            return getQualifiedName(scope, fullName);
        }

        var javaType = primitive ? type.protobufType().primitiveType() : type.protobufType().wrappedType();
        if(javaType.isArray()) {
            return javaType.getComponentType().getName() + "[]";
        }

        return javaType.getName();
    }

    private record MessageType(Type type, String rawType, String typeParameter, TypeDeclaration<?> wrapper, String fallbackWrapper) {
        public boolean hasWrapper(){
            return wrapper != null;
        }

        public boolean hasFallbackWrapper(){
            return fallbackWrapper != null;
        }
    }

    private CompactConstructorDeclaration getOrCreateCompactConstructor(RecordDeclaration ctRecord) {
        var compactConstructors = ctRecord.getCompactConstructors();
        if (!compactConstructors.isEmpty()) {
            return compactConstructors.get(0);
        }

        var compactConstructor = new CompactConstructorDeclaration(NodeList.nodeList(Modifier.publicModifier()), protoStatement.name());
        ctRecord.addMember(compactConstructor);
        return compactConstructor;
    }

    private void addNestedMessage(TypeDeclaration<?> ctRecord, ProtobufMessageStatement messageStatement) {
        var result = getTypeMember(ctRecord, messageStatement.name());
        if (result.isPresent()) {
            return;
        }

        var creator = new MessageSchemaCreator(packageName, messageStatement, mutable, classPool, output);
        creator.generate(ctRecord);
    }

    private void addNestedEnum(TypeDeclaration<?> ctRecord, ProtobufEnumStatement enumStatement) {
        var result = getTypeMember(ctRecord, enumStatement.name());
        if (result.isPresent()) {
            return;
        }
        
        var creator = new EnumSchemaCreator(packageName, enumStatement, classPool, output);
        creator.generate(ctRecord);
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
                var nullCheck = new BinaryExpr(fieldAccess, new NullLiteralExpr(), BinaryExpr.Operator.NOT_EQUALS);
                conditional.setCondition(nullCheck);
                var returnField = new ReturnStmt(fieldAccess);
                var thenBlock = new BlockStmt();
                thenBlock.addStatement(returnField);
                conditional.setThenStmt(thenBlock);
                ctMethodBody.addStatement(conditional);
            }else {
                var fieldAccess = new FieldAccessExpr(new ThisExpr(), oneOfFieldStatement.name());
                var returnField = new ReturnStmt(fieldAccess);
                ctMethodBody.addStatement(returnField);
            }

            if(typeDeclaration instanceof RecordDeclaration ctRecord){
                var result = addRecordParameter(oneOfFieldStatement, ctRecord, true);
                var type = addOneOfInterface(typeDeclaration, result, ctInterface);
                permittedTypes.add(type);
            }else if(typeDeclaration instanceof ClassOrInterfaceDeclaration ctClass){
                var result = addClassField(oneOfFieldStatement, ctClass, true);
                var type = addOneOfInterface(typeDeclaration, result, ctInterface);
                permittedTypes.add(type);
            }else {
                throw new IllegalArgumentException("Unknown type: " + typeDeclaration.getClass().getName());
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
        typeDeclaration.addMember(ctMethod);
        ctMethod.addModifier(Keyword.PUBLIC);
        ctMethod.setName(oneOfStatement.name());
        ctMethod.setType(ctInterface.getNameAsString());
        return ctMethod;
    }

    private ClassOrInterfaceDeclaration createOneOfInterface(TypeDeclaration<?> typeDeclaration, ProtobufOneOfStatement oneOfStatement) {
        var result = getTypeMember(typeDeclaration, oneOfStatement.className());
        if (result.isPresent()) {
            return (ClassOrInterfaceDeclaration) result.get();
        }

        var ctInterface = new ClassOrInterfaceDeclaration();
        typeDeclaration.addMember(ctInterface);
        ctInterface.addModifier(Keyword.PUBLIC);
        ctInterface.addModifier(Keyword.SEALED);
        ctInterface.setInterface(true);
        ctInterface.setName(oneOfStatement.className());
        return ctInterface;
    }

    private ClassOrInterfaceType addOneOfInterface(TypeDeclaration<?> scope, MessageType result, ClassOrInterfaceDeclaration ctInterface) {
        if (result.hasWrapper()) {
            addImplementedType(ctInterface, result.wrapper());
            return parseClassOrInterfaceType(getQualifiedName(scope, result.wrapper()));
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
            getDeferredImplementation(protoStatement.name())
                    .ifPresent(entry -> addImplementedType(entry, ctRecord));
            addRecordMembers(ctRecord);
            addReservedAnnotation(ctRecord);
            return Optional.of(result.get().compilationUnit());
        }

        var ctClass = (ClassOrInterfaceDeclaration) result.get().result();
        getDeferredImplementation(protoStatement.name()).ifPresent(entry -> {
            addImplementedType(entry, ctClass);
            ctClass.setFinal(true);
        });
        addClassMembers(ctClass);
        addAllArgsConstructor(ctClass);
        addReservedAnnotation(ctClass);
        return Optional.of(result.get().compilationUnit());
    }
}

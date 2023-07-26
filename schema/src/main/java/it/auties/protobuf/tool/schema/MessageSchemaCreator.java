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
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.parser.statement.ProtobufEnumStatement;
import it.auties.protobuf.parser.statement.ProtobufFieldStatement;
import it.auties.protobuf.parser.statement.ProtobufMessageStatement;
import it.auties.protobuf.parser.statement.ProtobufOneOfStatement;
import it.auties.protobuf.parser.type.ProtobufObjectType;
import it.auties.protobuf.parser.type.ProtobufTypeReference;
import lombok.NonNull;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.github.javaparser.StaticJavaParser.parseClassOrInterfaceType;
import static com.github.javaparser.StaticJavaParser.parseType;

final class MessageSchemaCreator extends SchemaCreator<ProtobufMessageStatement> {
    MessageSchemaCreator(@NonNull ProtobufMessageStatement protoStatement, boolean mutable, @NonNull List<CompilationUnit> classPool, Path output) {
        super(protoStatement, mutable, classPool, output);
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
        getDeferredImplementation(protoStatement.name())
                .ifPresent(entry -> addImplementedType(entry, ctRecord));
        addRecordMembers(ctRecord);
        addReservedAnnotation(ctRecord);
    }

    private void addAllArgsConstructor(ClassOrInterfaceDeclaration ctClass) {
        var constructor = new ConstructorDeclaration();
        ctClass.addMember(constructor);
        constructor.setPublic(true);
        constructor.setName(ctClass.getName());
        var body = new BlockStmt();
        constructor.setBody(body);
        for (var field : ctClass.getFields()) {
            var property = field.getAnnotationByClass(ProtobufProperty.class);
            if (property.isEmpty() || !(property.get() instanceof NormalAnnotationExpr annotationExpr)) {
                continue;
            }

            createAccessors(ctClass, field);
            var required = isRequired(annotationExpr);
            for (var variable : field.getVariables()) {
                var parameter = new Parameter();
                parameter.setType(variable.getType());
                parameter.setName(variable.getName());
                constructor.addParameter(parameter);
                var assignment = new AssignExpr();
                var fieldAccess = new FieldAccessExpr(new ThisExpr(), variable.getNameAsString());
                assignment.setTarget(fieldAccess);
                assignment.setOperator(Operator.ASSIGN);
                var value = getClassAssignmentValue(variable, required);
                assignment.setValue(value);
                body.addStatement(assignment);
            }
        }
    }

    private void createAccessors(ClassOrInterfaceDeclaration ctClass, FieldDeclaration field) {
        var accessor = new MethodDeclaration();
        accessor.setPublic(true);
        var sampleVariable = field.getVariable(0);
        accessor.setType(sampleVariable.getType());
        var methodName = sampleVariable.getNameAsString();
        accessor.setName(methodName);
        var accessorBody = new BlockStmt();
        accessor.setBody(accessorBody);
        var fieldAccess = new NameExpr(methodName);
        accessorBody.addStatement(new ReturnStmt(fieldAccess));
        ctClass.addMember(accessor);
        field.createSetter();
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

    private Optional<MessageType> addClassField(ProtobufFieldStatement fieldStatement, ClassOrInterfaceDeclaration ctClass, boolean wrapType) {
        var existing = getClassField(fieldStatement, ctClass);
        if(existing.isPresent()){
            return Optional.empty();
        }

        var parameterType = getMessageType(ctClass, fieldStatement, wrapType);
        var field = new FieldDeclaration(NodeList.nodeList(Modifier.publicModifier()), parameterType.type(), fieldStatement.name());
        var annotation = new NormalAnnotationExpr();
        annotation.setName(ProtobufProperty.class.getSimpleName());
        annotation.addPair("index", new IntegerLiteralExpr(String.valueOf(fieldStatement.index())));
        annotation.addPair("type", new FieldAccessExpr(new NameExpr(ProtobufType.class.getSimpleName()), fieldStatement.type().protobufType().name()));
        if(fieldStatement.required()) {
            annotation.addPair("required", new BooleanLiteralExpr(true));
        }
        if(fieldStatement.repeated()) {
            annotation.addPair("implementation", parameterType.typeParameter() + ".class");
            annotation.addPair("repeated", new BooleanLiteralExpr(true));
        }
        field.addAnnotation(annotation);
        ctClass.addMember(field);
        return Optional.of(parameterType);
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

    private Optional<MessageType> addRecordParameter(ProtobufFieldStatement fieldStatement, RecordDeclaration ctRecord, boolean wrapType) {
        var existing = getRecordParameter(fieldStatement, ctRecord);
        if(existing.isPresent()){
            return Optional.empty();
        }

        var parameterType = getMessageType(ctRecord, fieldStatement, wrapType);
        var parameter = new Parameter(parameterType.type(), fieldStatement.name());
        var annotation = new NormalAnnotationExpr();
        annotation.setName(ProtobufProperty.class.getSimpleName());
        annotation.addPair("index", new IntegerLiteralExpr(String.valueOf(fieldStatement.index())));
        annotation.addPair("type", new FieldAccessExpr(new NameExpr(ProtobufType.class.getSimpleName()), fieldStatement.type().protobufType().name()));
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
            annotation.addPair("implementation", parameterType.typeParameter() + ".class");
            annotation.addPair("repeated", new BooleanLiteralExpr(true));
        }
        parameter.addAnnotation(annotation);
        ctRecord.addParameter(parameter);
        return Optional.of(parameterType);
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
        var simpleType = getMessageFieldRawType(scope, !fieldStatement.repeated(), fieldStatement.type());
        var rawType = fieldStatement.repeated() ? List.class.getSimpleName() : simpleType;
        var typeParameter = fieldStatement.repeated() ? simpleType : null;
        var javaType = parseType(typeParameter == null ? rawType : "%s<%s>".formatted(rawType, typeParameter));
        if (!wrapType) {
            return new MessageType(javaType, rawType, typeParameter, null, null);
        }

        if (!fieldStatement.repeated() && (fieldStatement.type().protobufType() == ProtobufType.MESSAGE || fieldStatement.type().protobufType() == ProtobufType.ENUM)) {
            var fieldType = (ProtobufObjectType) fieldStatement.type();
            var wrapperType = getTypeDeclaration(fieldType.name(), QueryType.ANY);
            wrapperType.ifPresent(queryResult -> queryResult.result().addModifier(Keyword.FINAL));
            return new MessageType(javaType, rawType, typeParameter, wrapperType.map(QueryResult::result).orElse(null), wrapperType.isPresent() ? null : fieldType.name());
        }

        var wrapperRecordName = fieldStatement.name().substring(0, 1).toUpperCase() + fieldStatement.name().substring(1);
        var wrapperRecord = new RecordDeclaration(NodeList.nodeList(Modifier.publicModifier()), wrapperRecordName);
        var parameter = new Parameter(javaType, "value");
        wrapperRecord.addParameter(parameter);
        scope.addMember(wrapperRecord);
        return new MessageType(parseType(wrapperRecordName), rawType, typeParameter, wrapperRecord, null);
    }

    private String getMessageFieldRawType(TypeDeclaration<?> scope, boolean primitive, ProtobufTypeReference type) {
        if (type instanceof ProtobufObjectType messageType) {
            var qualifiedName = messageType.declaration().qualifiedCanonicalName();
            var fullName = packageName != null ? "%s.%s".formatted(packageName, qualifiedName) : qualifiedName;
            return qualifiedMinimalName(scope, fullName);
        }

        var javaType = primitive ? type.protobufType().primitiveType() : type.protobufType().wrappedType();
        return javaType.getSimpleName();
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
        var creator = new MessageSchemaCreator(messageStatement, mutable, classPool, output);
        creator.generate(ctRecord);
    }

    private void addNestedEnum(TypeDeclaration<?> ctRecord, ProtobufEnumStatement enumStatement) {
        var creator = new EnumSchemaCreator(enumStatement, classPool, output);
        creator.generate(ctRecord);
    }

    private void addOneOfStatement(TypeDeclaration<?> typeDeclaration, ProtobufOneOfStatement oneOfStatement) {
        var ctInterface = new ClassOrInterfaceDeclaration();
        typeDeclaration.addMember(ctInterface);
        ctInterface.addModifier(Keyword.PUBLIC);
        ctInterface.addModifier(Keyword.SEALED);
        ctInterface.setInterface(true);
        ctInterface.setName(oneOfStatement.className());
        var ctMethod = new MethodDeclaration();
        ctMethod.addModifier(Keyword.PUBLIC);
        ctMethod.setName(oneOfStatement.name());
        var ctMethodBody = new BlockStmt();
        ctMethod.setBody(ctMethodBody);
        ctMethod.setType(ctInterface.getNameAsString());
        var permittedTypes = new NodeList<ClassOrInterfaceType>();
        for (var oneOfFieldStatement : oneOfStatement.statements()) {
            var conditional = new IfStmt();
            var fieldAccess = new FieldAccessExpr(new ThisExpr(), oneOfFieldStatement.name());
            var nullCheck = new BinaryExpr(fieldAccess, new NullLiteralExpr(), BinaryExpr.Operator.NOT_EQUALS);
            conditional.setCondition(nullCheck);
            var returnField = new ReturnStmt(fieldAccess);
            var thenBlock = new BlockStmt();
            thenBlock.addStatement(returnField);
            conditional.setThenStmt(thenBlock);
            ctMethodBody.addStatement(conditional);
            if(typeDeclaration instanceof RecordDeclaration ctRecord){
                addRecordParameter(oneOfFieldStatement, ctRecord, true)
                        .ifPresent(result -> onOneOfFieldAdded(typeDeclaration, result, permittedTypes, ctInterface));
            }else if(typeDeclaration instanceof ClassOrInterfaceDeclaration ctClass){
                addClassField(oneOfFieldStatement, ctClass, true)
                        .ifPresent(result -> onOneOfFieldAdded(typeDeclaration, result, permittedTypes, ctInterface));
            }else {
                throw new IllegalArgumentException("Unknown type: " + typeDeclaration.getClass().getName());
            }
        }
        ctMethodBody.addStatement(new ReturnStmt(new NullLiteralExpr()));
        ctInterface.setPermittedTypes(permittedTypes);
        typeDeclaration.addMember(ctMethod);
    }

    private void onOneOfFieldAdded(TypeDeclaration<?> scope, MessageType result, NodeList<ClassOrInterfaceType> permittedTypes, ClassOrInterfaceDeclaration ctInterface) {
        if (result.hasWrapper()) {
            addImplementedType(ctInterface, result.wrapper());
            permittedTypes.add(parseClassOrInterfaceType(qualifiedMinimalName(scope, result.wrapper())));
        } else if (result.hasFallbackWrapper()) {
            addOneOfDeferredImplementation(result.fallbackWrapper(), ctInterface);
            permittedTypes.add(parseClassOrInterfaceType(result.fallbackWrapper()));
        } else {
            throw new RuntimeException("Invalid wrapper for oneof statement");
        }
    }

    @Override
    Optional<CompilationUnit> update(String name) {
        var result = getTypeDeclaration(name, QueryType.MESSAGE);
        if (result.isEmpty()) {
            return Optional.of(generate());
        }

        if(result.get().result() instanceof RecordDeclaration ctRecord){
            ctRecord.setImplementedTypes(NodeList.nodeList());
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

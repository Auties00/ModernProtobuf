package it.auties.protobuf.tool.schema;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.nodeTypes.NodeWithImplements;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufProperty;
import it.auties.protobuf.base.ProtobufType;
import it.auties.protobuf.parser.statement.ProtobufEnumStatement;
import it.auties.protobuf.parser.statement.ProtobufFieldStatement;
import it.auties.protobuf.parser.statement.ProtobufMessageStatement;
import it.auties.protobuf.parser.statement.ProtobufOneOfStatement;
import it.auties.protobuf.parser.type.ProtobufMessageType;
import it.auties.protobuf.parser.type.ProtobufTypeReference;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.github.javaparser.StaticJavaParser.parseClassOrInterfaceType;
import static com.github.javaparser.StaticJavaParser.parseType;

final class MessageSchemaCreator extends SchemaCreator<ProtobufMessageStatement> {
    MessageSchemaCreator(ProtobufMessageStatement protoStatement, boolean mutable, List<CompilationUnit> classPool) {
        super(protoStatement, mutable, classPool);
    }

    @Override
    CompilationUnit generate(Path output) {
        var compilationUnit = createCompilationUnit();
        compilationUnit.addType(generate());
        return compilationUnit;
    }

    @Override
    TypeDeclaration<RecordDeclaration> generate() {
        var ctRecord = new RecordDeclaration(NodeList.nodeList(Modifier.publicModifier()), protoStatement.name());
        ctRecord.setImplementedTypes(NodeList.nodeList(parseClassOrInterfaceType(ProtobufMessage.class.getSimpleName())));
        getDeferredImplementation(protoStatement.name())
                .ifPresent(entry -> ctRecord.addImplementedType(entry.getNameAsString()));
        addMembers(ctRecord);
        addReservedAnnotation(ctRecord);
        return ctRecord;
    }


    private void addMembers(RecordDeclaration ctRecord) {
        for(var statement : protoStatement.statements()){
            if(statement instanceof ProtobufFieldStatement fieldStatement){
                addRecordParameter(fieldStatement, ctRecord, false);
            }else if(statement instanceof ProtobufMessageStatement messageStatement){
                addNestedRecord(ctRecord, messageStatement);
            }else if(statement instanceof ProtobufEnumStatement enumStatement){
                addNestedEnum(ctRecord, enumStatement);
            }else if (statement instanceof ProtobufOneOfStatement oneOfStatement){
                addOneOfStatement(ctRecord, oneOfStatement);
            }
        }
    }

    private RecordParameterResult addRecordParameter(ProtobufFieldStatement fieldStatement, RecordDeclaration ctRecord, boolean wrapType) {
        var recordComponent = getRecordComponentType(fieldStatement);
        var parameterType = getRecordParameterType(fieldStatement, ctRecord, recordComponent, wrapType);
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
            var selectFieldExpression = new FieldAccessExpr(new ThisExpr(), fieldStatement.name());
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

    private FieldStatementRecordType getRecordComponentType(ProtobufFieldStatement fieldStatement) {
        if(fieldStatement.repeated()){
            var name = getRecordComponentRawType(fieldStatement.type(), false);
            return new FieldStatementRecordType(List.class.getSimpleName(), name);
        }

        var componentType = getRecordComponentRawType(fieldStatement.type(), true);
        return new FieldStatementRecordType(componentType);
    }

    private String getRecordComponentRawType(ProtobufTypeReference type, boolean primitive) {
        if (type instanceof ProtobufMessageType messageType) {
            return messageType.name();
        }

        var javaType = primitive ? type.protobufType().primitiveType() : type.protobufType().wrappedType();
        if(javaType.isArray()){
            return javaType.getSimpleName() + "[]";
        }

        return javaType.getSimpleName();
    }

    private RecordParameterResult getRecordParameterType(ProtobufFieldStatement fieldStatement, RecordDeclaration recordDeclaration, FieldStatementRecordType recordComponent, boolean wrapType) {
        if (!wrapType) {
            return new RecordParameterResult(parseType(recordComponent.toString()), null, null);
        }

        if (!fieldStatement.repeated() && fieldStatement.type().protobufType() == ProtobufType.MESSAGE) {
            var fieldType = (ProtobufMessageType) fieldStatement.type();
            var wrapperType = getCompilationUnit(fieldType.name())
                    .orElse(null);
            return new RecordParameterResult(parseType(recordComponent.toString()), wrapperType, wrapperType != null ? null : fieldType.name());
        }

        var wrapperRecordName = fieldStatement.name().substring(0, 1).toUpperCase() + fieldStatement.name().substring(1);
        var wrapperRecord = new RecordDeclaration(NodeList.nodeList(Modifier.publicModifier()), wrapperRecordName);
        wrapperRecord.setImplementedTypes(NodeList.nodeList(parseClassOrInterfaceType(ProtobufMessage.class.getSimpleName())));
        var parameter = new Parameter(parseType(recordComponent.toString()), "value");
        wrapperRecord.addParameter(parameter);
        recordDeclaration.addMember(wrapperRecord);
        return new RecordParameterResult(parseType(wrapperRecordName), wrapperRecord, null);
    }

    private record RecordParameterResult(Type type, TypeDeclaration<?> wrapper, String fallbackWrapper) {
        public boolean hasWrapper(){
            return wrapper != null;
        }

        public boolean hasFallbackWrapper(){
            return fallbackWrapper != null;
        }
    }

    private CompactConstructorDeclaration getOrCreateCompactConstructor(RecordDeclaration ctRecord) {
        var compactConstructors = ctRecord.getCompactConstructors();
        if(compactConstructors.isEmpty()){
            var compactConstructor = new CompactConstructorDeclaration(NodeList.nodeList(Modifier.publicModifier()), protoStatement.name());
            ctRecord.addMember(compactConstructor);
            return compactConstructor;
        }

        return compactConstructors.get(0);
    }

    private record FieldStatementRecordType(String type, String parameter) {
        private FieldStatementRecordType(String type) {
            this(type, null);
        }

        @Override
        public String toString() {
            return Optional.ofNullable(parameter)
                    .map(paramName -> "%s<%s>".formatted(type, paramName))
                    .orElseGet(this::type);
        }
    }

    private void addNestedRecord(RecordDeclaration ctRecord, ProtobufMessageStatement messageStatement) {
        var creator = new MessageSchemaCreator(messageStatement, mutable, classPool);
        ctRecord.addMember(creator.generate());
    }

    private void addNestedEnum(RecordDeclaration ctRecord, ProtobufEnumStatement enumStatement) {
        var creator = new EnumSchemaCreator(enumStatement, mutable, classPool);
        ctRecord.addMember(creator.generate());
    }

    private void addOneOfStatement(RecordDeclaration ctRecord, ProtobufOneOfStatement oneOfStatement) {
        var ctInterface = new ClassOrInterfaceDeclaration();
        ctInterface.addModifier(Modifier.Keyword.PUBLIC);
        ctInterface.addModifier(Modifier.Keyword.SEALED);
        ctInterface.setInterface(true);
        ctInterface.setName(oneOfStatement.className());
        ctInterface.setExtendedTypes(NodeList.nodeList(parseClassOrInterfaceType(ProtobufMessage.class.getSimpleName())));
        var ctMethod = new MethodDeclaration();
        ctMethod.addModifier(Modifier.Keyword.PUBLIC);
        ctMethod.setName(oneOfStatement.name());
        var ctMethodBody = new BlockStmt();
        ctMethod.setBody(ctMethodBody);
        ctMethod.setType(ctInterface.getNameAsString());
        var permittedTypes = new NodeList<ClassOrInterfaceType>();
        for (var oneOfFieldStatement : oneOfStatement.statements()) {
            var conditional = new IfStmt();
            var fieldAccess = new FieldAccessExpr(new ThisExpr(), oneOfStatement.name());
            var nullCheck = new BinaryExpr(fieldAccess, new NullLiteralExpr(), BinaryExpr.Operator.NOT_EQUALS);
            conditional.setCondition(nullCheck);
            var returnField = new ReturnStmt(fieldAccess);
            var thenBlock = new BlockStmt();
            thenBlock.addStatement(returnField);
            conditional.setThenStmt(thenBlock);
            ctMethodBody.addStatement(conditional);
            var result = addRecordParameter(oneOfFieldStatement, ctRecord, true);
            if (result.hasWrapper()) {
                var wrapper = (NodeWithImplements<?>) result.wrapper();
                wrapper.addImplementedType(oneOfStatement.className());
                permittedTypes.add(parseClassOrInterfaceType(result.wrapper().getNameAsString()));
            }else if(result.hasFallbackWrapper()){
                addOneOfDeferredImplementation(result.fallbackWrapper(), ctInterface);
                permittedTypes.add(parseClassOrInterfaceType(result.fallbackWrapper()));
            }else {
                throw new RuntimeException("Invalid wrapper for oneof statement");
            }
        }
        ctMethodBody.addStatement(new ReturnStmt(new NullLiteralExpr()));
        ctInterface.setPermittedTypes(permittedTypes);
        ctRecord.addMember(ctMethod);
        ctRecord.addMember(ctInterface);
    }

    @Override
    CompilationUnit update(Path output) {
        return null;
    }

    @Override
    TypeDeclaration<?> update() {
        return null;
    }
}

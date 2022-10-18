package it.auties.protobuf.tool.schema;

import it.auties.protobuf.base.ProtobufType;
import it.auties.protobuf.parser.statement.*;
import it.auties.protobuf.parser.type.ProtobufPrimitiveType;
import it.auties.protobuf.tool.util.AstElements;
import it.auties.protobuf.tool.util.AstUtils;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.reflect.reference.CtArrayTypeReferenceImpl;

import java.util.*;
import java.util.stream.Collectors;

public final class MessageSchemaCreator extends SchemaCreator<CtClass<?>, ProtobufMessageStatement> {
    public MessageSchemaCreator(CtClass<?> ctType, ProtobufMessageStatement protoStatement, Factory factory) {
        super(ctType, protoStatement, factory);
    }

    public MessageSchemaCreator(ProtobufMessageStatement protoStatement, Factory factory) {
        super(protoStatement, factory);
    }

    public MessageSchemaCreator(CtClass<?> ctType, CtType<?> parent, ProtobufMessageStatement protoStatement, Factory factory) {
        super(ctType, parent, protoStatement, factory);
    }

    @Override
    public CtClass<?> createSchema() {
        this.ctType = createClass();
        createMessage(true);
        return ctType;
    }

    @Override
    public CtClass<?> update() {
        Objects.requireNonNull(ctType, "Cannot update type without it");
        createMessage(false);
        return ctType;
    }

    private void createMessage(boolean force) {
        protoStatement.statements()
                .forEach(statement -> createMessageStatement(statement, force));
        createReservedMethod(true, force);
        createReservedMethod(false, force);
    }

    private void createReservedMethod(boolean indexes, boolean force){
        var methodName = indexes ? "reservedFieldIndexes" : "reservedFieldNames";
        if(!force){
            var existing = ctType.getMethod(methodName);
            if(existing != null){
                return;
            }
        }

        var elements = indexes ? protoStatement.reservedIndexes() : protoStatement.reservedNames();
        if(elements.isEmpty()){
            return;
        }

        var returnType = factory.Type().createReference(AstElements.LIST);
        var returnTypeArg = indexes ? factory.Type().integerType() : factory.Type().stringType();
        returnType.addActualTypeArgument(returnTypeArg);
        var method = factory.createMethod(
                ctType,
                Set.of(ModifierKind.PUBLIC),
                returnType,
                methodName,
                List.of(),
                Set.of()
        );
        method.addAnnotation(factory.createAnnotation(factory.createReference(AstElements.OVERRIDE)));
        var body = factory.createBlock();
        var returnStatement = factory.createReturn();
        var ofMethod = factory.Method().createReference(
                factory.createReference(AstElements.LIST),
                factory.createReference(AstElements.LIST),
                "of",
                factory.createArrayTypeReference()
        );
        var literals = elements.stream()
                .map(factory::createLiteral)
                .collect(Collectors.toCollection(ArrayList<CtExpression<?>>::new));
        var ofInvocation = factory.createInvocation(
                factory.createTypeAccess(factory.createReference(AstElements.ARRAYS)),
                ofMethod,
                literals
        );
        returnStatement.setReturnedExpression(ofInvocation);
        body.addStatement(returnStatement);
        method.setBody(body);
    }

    private void createMessageStatement(ProtobufStatement statement, boolean force) {
        if (statement instanceof ProtobufMessageStatement messageStatement) {
            createNestedMessage(messageStatement, force);
            return;
        }

        if (statement instanceof ProtobufEnumStatement enumStatement) {
            createNestedEnum(enumStatement, force);
            return;
        }

        if (statement instanceof ProtobufOneOfStatement oneOfStatement) {
            var oneOfEnum = createOneOfEnum(oneOfStatement, force);
            oneOfStatement.statements()
                    .forEach(fieldStatement -> createField(fieldStatement, force));
            createOneOfMethod(oneOfStatement, oneOfEnum, force);
            return;
        }

        if (statement instanceof ProtobufFieldStatement fieldStatement) {
            createField(fieldStatement, force);
            return;
        }

        throw new UnsupportedOperationException("Cannot create schema for statement: " + statement);
    }


    private void createOneOfMethod(ProtobufOneOfStatement oneOfStatement, CtEnum<?> oneOfEnum, boolean force) {
        var methodName = "%sType".formatted(oneOfStatement.name());
        if(!force) {
            var existing = ctType.getMethod(methodName);
            if (existing != null) {
                return;
            }
        }

        var body = factory.createBlock();
        var iterator = oneOfStatement.statements().iterator();
        var nullTarget = factory.createLiteral(null);
        while (iterator.hasNext()){
            var entry = iterator.next();
            if (!iterator.hasNext()) {
                var returnStatement = createReturn(oneOfEnum, entry);
                body.addStatement(returnStatement);
                continue;
            }

            var check = factory.createIf();
            var fieldRead = createFieldRead(entry);
            CtBinaryOperator<Boolean> condition = factory.createBinaryOperator(fieldRead, nullTarget, BinaryOperatorKind.NE);
            check.setCondition(condition);
            var returnStatement = createReturn(oneOfEnum, entry);
            check.setThenStatement(returnStatement);
            body.addStatement(check);
        }

        var method = factory.createMethod(
                ctType,
                Set.of(ModifierKind.PUBLIC),
                oneOfEnum.getReference(),
                methodName,
                List.of(),
                Set.of()
        );
        method.setBody(body);
    }

    private void createField(ProtobufFieldStatement fieldStatement, boolean force) {
        var existingField = ctType.getField(fieldStatement.name());
        var existingAccessor = ctType.getMethod(fieldStatement.name());
        if(!force && existingField != null && existingAccessor != null){
            return;
        }

        if(existingField == null){
            existingField = createClassField(fieldStatement, force);
        }

        if(existingAccessor != null || force){
            createFieldAccessor(fieldStatement, existingField);
        }
    }

    private CtField<?> createClassField(ProtobufFieldStatement fieldStatement, boolean force) {
        CtField<?> ctField = factory.createField(
                ctType,
                Set.of(ModifierKind.PRIVATE),
                AstUtils.createReference(fieldStatement, true, factory),
                fieldStatement.name()
        );
        if(fieldStatement.reference().type() == ProtobufType.BYTES){
            ctField.getType().putMetadata("DeclarationKind", CtArrayTypeReferenceImpl.DeclarationKind.TYPE);
        }

        var annotation = factory.createAnnotation(factory.createReference(AstElements.PROTOBUF_PROPERTY));
        annotation.addValue("index", fieldStatement.index());
        annotation.addValue("type", fieldStatement.reference().type());
        if(fieldStatement.required()){
            annotation.addValue("required", true);
            var nonNull = factory.createAnnotation(factory.createReference(AstElements.NON_NULL));
            ctField.addAnnotation(nonNull);
        }

        if(fieldStatement.repeated()){
            annotation.addValue("repeated", true);
            annotation.addValue("implementation", factory.createClassAccess(AstUtils.createReference(fieldStatement, false, factory)));
            createBuilderMethod(fieldStatement, ctField, force);
        }

        if(fieldStatement.packed()){
            annotation.addValue("packed", true);
        }

        if (fieldStatement.deprecated()) {
            var deprecated = factory.createAnnotation(factory.createReference(AstElements.DEPRECATED));
            ctField.addAnnotation(deprecated);
        }

        if (fieldStatement.defaultValue() != null) {
            var defaultBuilder = factory.createAnnotation(factory.createReference(AstElements.DEFAULT));
            ctField.addAnnotation(defaultBuilder);
            createDefaultExpression(ctField, fieldStatement);
        }

        annotation.addValue("name", fieldStatement.name());
        ctField.addAnnotation(annotation);
        return ctField;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void createFieldAccessor(ProtobufFieldStatement fieldStatement, CtField<?> ctField) {
        var returnType = createFieldAccessorType(fieldStatement, ctField);
        var accessor = factory.createMethod(
                ctType,
                Set.of(ModifierKind.PUBLIC),
                returnType,
                ctField.getSimpleName(),
                List.of(),
                Set.of()
        );

        var body = factory.createBlock();
        accessor.setBody(body);
        CtReturn returnStatement = factory.createReturn();
        if(fieldStatement.required() || fieldStatement.repeated()) {
            returnStatement.setReturnedExpression(createFieldRead(fieldStatement));
        }else {
            var optionalType = factory.createReference(AstElements.OPTIONAL);;
            var ofMethod = factory.Method().createReference(
                    optionalType,
                    optionalType,
                    "ofNullable",
                    ctField.getType()
            );
            var ofInvocation = factory.createInvocation(
                    factory.createTypeAccess(optionalType),
                    ofMethod,
                    createFieldRead(fieldStatement)
            );
            returnStatement.setReturnedExpression(ofInvocation);
        }

        body.addStatement(returnStatement);
    }

    private CtTypeReference<?> createFieldAccessorType(ProtobufFieldStatement fieldStatement, CtField<?> ctField) {
        if(fieldStatement.required() || fieldStatement.repeated()){
            return ctField.getType();
        }

        var returnType = factory.createReference(AstElements.OPTIONAL);
        returnType.addActualTypeArgument(ctField.getType());
        return returnType;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void createBuilderMethod(ProtobufFieldStatement fieldStatement, CtField<?> ctField, boolean force) {
        var builderClass = getOrCreateBuilder();
        if(!force){
            var existing = builderClass.getMethod(fieldStatement.name());
            if(existing != null){
                return;
            }
        }

        var existing = factory.createMethod(
                builderClass,
                Set.of(ModifierKind.PUBLIC),
                builderClass.getReference(),
                fieldStatement.name(),
                List.of(),
                Set.of()
        );

        var parameter = factory.createParameter(
                existing,
                ctField.getType(),
                fieldStatement.name()
        );

        var body = factory.createBlock();

        CtFieldReference localFieldReference = factory.Field().createReference(
                builderClass.getReference(),
                ctField.getType(),
                fieldStatement.name()
        );

        var localFieldRead = factory.createFieldRead();
        localFieldRead.setTarget(factory.createThisAccess(builderClass.getReference()));
        localFieldRead.setVariable(localFieldReference);

        CtBinaryOperator<Boolean> isNullCondition = factory.createBinaryOperator(
                localFieldRead,
                factory.createLiteral(null),
                BinaryOperatorKind.EQ
        );

        var newArrayList = factory.createConstructorCall(
                factory.createReference(AstElements.ARRAY_LIST)
        );
        var newArrayListDiamond = AstUtils.createReference(fieldStatement, false, factory);
        newArrayListDiamond.setImplicit(true);
        newArrayList.getType().addActualTypeArgument(newArrayListDiamond);

        var isNullThen = factory.createVariableAssignment(
                localFieldReference,
                false,
                newArrayList
        );

        var isNullIf = factory.createIf();
        isNullIf.setCondition(isNullCondition);
        isNullIf.setThenStatement(isNullThen);
        body.addStatement(isNullIf);

        var addAllMethod = factory.Method().createReference(
                builderClass.getReference(),
                localFieldRead.getType(),
                "addAll",
                factory.createReference(AstElements.COLLECTION)
        );
        var addAllInvocation = factory.createInvocation(
                localFieldRead,
                addAllMethod,
                factory.createVariableRead(factory.createParameterReference(parameter), false)
        );
        body.addStatement(addAllInvocation);

        CtReturn returnStatement = factory.createReturn();
        returnStatement.setReturnedExpression(factory.createThisAccess(builderClass.getReference()));
        body.addStatement(returnStatement);

        existing.setBody(body);
    }

    private CtClass<?> getOrCreateBuilder() {
        var existing = AstUtils.getBuilderClass(ctType);
        if (existing != null) {
            return existing;
        }

        CtClass<?> builderClass = factory.createClass("%sBuilder".formatted(ctType.getSimpleName()));
        builderClass.setModifiers(Set.of(ModifierKind.PUBLIC, ModifierKind.STATIC));
        builderClass.setParent(ctType);
        ctType.addNestedType(builderClass);
        return builderClass;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void createDefaultExpression(CtField ctField, ProtobufFieldStatement fieldStatement) {
        var literal = factory.createLiteral(fieldStatement.defaultValue());
        ctField.setDefaultExpression(literal);
    }

    private CtEnum<?> createOneOfEnum(ProtobufOneOfStatement oneOfStatement, boolean force) {
        var enumStatement = new ProtobufEnumStatement(oneOfStatement.className(), oneOfStatement.packageName(), oneOfStatement.parent());
        enumStatement.addStatement(new ProtobufFieldStatement("unknown", enumStatement.packageName(), oneOfStatement.parent()).index(0));
        oneOfStatement.statements().forEach(enumStatement::addStatement);
        return createNestedEnum(enumStatement, force);
    }

    private CtEnum<?> createNestedEnum(ProtobufEnumStatement enumStatement, boolean force) {
        if(!force){
            var existing = (CtEnum<?>) AstUtils.getProtobufClass(ctType, enumStatement);
            if(existing != null) {
                return existing;
            }
        }

        var creator = new EnumSchemaCreator(null, ctType, enumStatement, factory);
        var result = creator.createSchema();
        ctType.addNestedType(result);
        return result;
    }

    private void createNestedMessage(ProtobufMessageStatement messageStatement, boolean force) {
        if(!force){
            var existing = AstUtils.getProtobufClass(ctType, messageStatement);
            if(existing != null) {
                return;
            }
        }

        var creator = new MessageSchemaCreator(null, ctType, messageStatement, factory);
        var result = creator.createSchema();
        ctType.addNestedType(result);
    }

    private CtClass<?> createClass() {
        CtClass<?> ctClass = factory.createClass(protoStatement.qualifiedName());
        if(protoStatement.nested()) {
            Objects.requireNonNull(parent, "Missing parent during AST generation");
            ctClass.setParent(parent);
        }

        ctClass.setModifiers(Set.of(ModifierKind.PUBLIC));
        ctClass.addSuperInterface(factory.createReference(AstElements.PROTOBUF_MESSAGE));
        ctClass.addAnnotation(factory.createAnnotation(factory.createReference(AstElements.JACKSONIZED)));
        ctClass.addAnnotation(factory.createAnnotation(factory.createReference(AstElements.BUILDER)));
        return ctClass;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private CtFieldRead createFieldRead(ProtobufFieldStatement entry) {
        CtFieldRead fieldRead = factory.createFieldRead();
        fieldRead.setType(ctType.getReference());
        fieldRead.setVariable(ctType.getField(entry.name()).getReference());
        return fieldRead;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private CtReturn createReturn(CtEnum<?> javaEnum, ProtobufFieldStatement entry) {
        CtEnumValue constant = javaEnum.getEnumValue(entry.nameAsConstant());
        Objects.requireNonNull(constant, "Missing constant %s from enum".formatted(entry.nameAsConstant()));
        constant.setType(javaEnum.getReference());

        CtFieldRead fieldRead = factory.createFieldRead();
        fieldRead.setType(javaEnum.getReference());
        fieldRead.setVariable(constant.getReference());
        fieldRead.setTarget(factory.Code().createTypeAccess(javaEnum.getReference()));

        return factory.createReturn()
                .setReturnedExpression(fieldRead);
    }
}

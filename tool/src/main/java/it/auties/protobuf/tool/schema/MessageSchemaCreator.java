package it.auties.protobuf.tool.schema;

import it.auties.protobuf.base.ProtobufName;
import it.auties.protobuf.base.ProtobufProperty;
import it.auties.protobuf.base.ProtobufType;
import it.auties.protobuf.parser.statement.*;
import it.auties.protobuf.tool.util.AstElements;
import it.auties.protobuf.tool.util.AstUtils;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtReturn;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.reflect.reference.CtArrayTypeReferenceImpl;

import java.util.*;
import java.util.stream.Collectors;

public final class MessageSchemaCreator extends SchemaCreator<CtClass<?>, ProtobufMessageStatement> {
    private static final List<Class<? extends ProtobufStatement>> ORDER = List.of(
            ProtobufFieldStatement.class,
            ProtobufOneOfStatement.class,
            ProtobufMessageStatement.class,
            ProtobufEnumStatement.class
    );

    public MessageSchemaCreator(CtClass<?> ctType, ProtobufMessageStatement protoStatement, boolean accessors, Factory factory) {
        super(ctType, protoStatement, accessors, factory);
    }

    public MessageSchemaCreator(ProtobufMessageStatement protoStatement, boolean accessors, Factory factory) {
        super(protoStatement, accessors, factory);
    }

    public MessageSchemaCreator(CtClass<?> ctType, CtType<?> parent, ProtobufMessageStatement protoStatement, boolean accessors, Factory factory) {
        super(ctType, parent, protoStatement, accessors, factory);
    }

    @Override
    public CtClass<?> createSchema() {
        this.ctType = createClass();
        createMessage();
        return ctType;
    }

    @Override
    public CtClass<?> update() {
        this.ctType = Objects.requireNonNullElseGet(ctType, this::createClass);
        createMessage();
        return ctType;
    }

    private void createMessage() {
        protoStatement.statements()
                .stream()
                .sorted(Comparator.comparingInt(entry -> ORDER.indexOf(entry.getClass())))
                .forEach(this::createMessageStatement);
        createReservedAnnotation();
    }

    private void createReservedAnnotation() {
        if (protoStatement.reservedIndexes().isEmpty() && protoStatement.reservedNames().isEmpty()) {
            return;
        }

        var annotation = factory.createAnnotation(factory.createReference(AstElements.PROTOBUF_RESERVED));
        annotation.addValue("names", protoStatement.reservedNames().toArray(String[]::new));
        annotation.addValue("indexes", protoStatement.reservedIndexes().stream().mapToInt(entry -> entry).toArray());
        ctType.addAnnotation(annotation);
    }

    private void createMessageStatement(ProtobufStatement statement) {
        if (statement instanceof ProtobufMessageStatement messageStatement) {
            createNestedMessageWithLookup(messageStatement);
            return;
        }

        if (statement instanceof ProtobufEnumStatement enumStatement) {
            createNestedEnumWithLookup(enumStatement);
            return;
        }

        if (statement instanceof ProtobufOneOfStatement oneOfStatement) {
            createNestedOneOf(oneOfStatement);
            return;
        }

        if (statement instanceof ProtobufFieldStatement fieldStatement) {
            createField(fieldStatement);
            return;
        }

        throw new UnsupportedOperationException("Cannot create schema for statement: " + statement);
    }

    private boolean createField(ProtobufFieldStatement fieldStatement) {
        var existingField = getExistingField(fieldStatement);
        var field = createFieldInternal(fieldStatement, existingField);
        var accessor = ctType.getMethod(fieldStatement.name());
        if(accessor == null){
            createFieldAccessor(fieldStatement, field);
        }

        return existingField != null;
    }

    private CtField<?> createFieldInternal(ProtobufFieldStatement fieldStatement, CtField<?> existingField) {
        if(existingField == null){
            return createProtobufProperty(fieldStatement);
        }

        if (!fieldStatement.type().type().isMessage()) {
            return existingField;
        }

        var expectedName = fieldStatement.type().name();
        var actualName = existingField.getType().getSimpleName();
        if (Objects.equals(expectedName, actualName)) {
            return existingField;
        }

        var target = existingField.getType();
        if (target.getDeclaration() == null || hasProtobufMessageName(target.getDeclaration())) {
            return existingField;
        }

        var name = factory.createAnnotation(
                factory.createReference(AstElements.PROTOBUF_MESSAGE_NAME)
        );
        name.addValue("value", expectedName);
        target.getDeclaration().addAnnotation(name);
        return existingField;
    }

    private boolean hasProtobufMessageName(CtType<?> target) {
        return target.getAnnotations()
                .stream()
                .anyMatch(entry -> entry.getName().equalsIgnoreCase(ProtobufName.class.getSimpleName()));
    }

    private CtField<?> getExistingField(ProtobufFieldStatement fieldStatement) {
        return ctType.getFields()
                .stream()
                .filter(entry -> {
                    var annotation = entry.getAnnotation(ProtobufProperty.class);
                    return annotation != null && annotation.index() == fieldStatement.index();
                })
                .findFirst()
                .orElse(null);
    }

    private CtField<?> createProtobufProperty(ProtobufFieldStatement fieldStatement) {
        CtField<?> ctField = factory.createField(
                ctType,
                Set.of(ModifierKind.PRIVATE),
                AstUtils.createReference(fieldStatement, true, factory),
                fieldStatement.name()
        );
        if(fieldStatement.type().type() == ProtobufType.BYTES){
            ctField.getType().putMetadata("DeclarationKind", CtArrayTypeReferenceImpl.DeclarationKind.TYPE);
        }

        var annotation = factory.createAnnotation(factory.createReference(AstElements.PROTOBUF_PROPERTY));
        annotation.addValue("index", fieldStatement.index());
        annotation.addValue("type", fieldStatement.type().type());
        if(fieldStatement.required()){
            annotation.addValue("required", true);
            var nonNull = factory.createAnnotation(factory.createReference(AstElements.NON_NULL));
            ctField.addAnnotation(nonNull);
        }

        if(fieldStatement.repeated()){
            annotation.addValue("repeated", true);
            annotation.addValue("implementation", factory.createClassAccess(AstUtils.createReference(fieldStatement, false, factory)));
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
        if(!accessors){
            return;
        }

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
            var optionalType = factory.createReference(AstElements.OPTIONAL);
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
    private void createDefaultExpression(CtField ctField, ProtobufFieldStatement fieldStatement) {
        var literal = factory.createLiteral(fieldStatement.defaultValue());
        ctField.setDefaultExpression(literal);
    }

    private void createNestedOneOf(ProtobufOneOfStatement oneOfStatement) {
        var updating = createOneOfStatements(oneOfStatement);
        var enumDescriptor = createOneOfEnumDescriptor(oneOfStatement, updating);
        createOneOfMethod(oneOfStatement, enumDescriptor);
    }

    private void createOneOfMethod(ProtobufOneOfStatement oneOfStatement, CtEnum<?> oneOfEnum) {
        var existing = ctType.getMethods()
                .stream()
                .filter(entry -> entry.getType().getSimpleName().equals(oneOfEnum.getSimpleName()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            return;
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
            var block = factory.createBlock();
            block.addStatement(createReturn(oneOfEnum, entry));
            check.setThenStatement(block);
            body.addStatement(check);
        }

        var method = factory.createMethod(
                ctType,
                Set.of(ModifierKind.PUBLIC),
                oneOfEnum.getReference(),
                oneOfStatement.methodName(),
                List.of(),
                Set.of()
        );
        method.setBody(body);
    }

    private boolean createOneOfStatements(ProtobufOneOfStatement oneOfStatement) {
        return oneOfStatement.statements()
                .stream()
                .anyMatch(this::createField);
    }

    private CtEnum<?> createOneOfEnumDescriptor(ProtobufOneOfStatement oneOfStatement, boolean updatingInnerScope) {
        var existing = !updating ? null : (CtEnum<?>) AstUtils.getProtobufClass(factory.getModel(), oneOfStatement.className(), true);
        if (existing != null) {
            return existing;
        }

        var enumStatement = createOneOfEnum(oneOfStatement);
        if(!updating || !updatingInnerScope){
            return createNestedEnum(enumStatement, null);
        }

        var possibleEnumType = getExistingOneOfDescriptor(enumStatement);
        if (possibleEnumType != null) {
            log.info("Oneof statement %s in %s doesn't have an enum descriptor(expected %s)."
                    .formatted(oneOfStatement.name(), oneOfStatement.parent().name(), oneOfStatement.className()));
            log.info("%s looks like the missing descriptor, but no name override was specified using @ProtobufName so this is just speculation."
                    .formatted(ctType.getSimpleName()));
            log.info("Type yes to use this enum, otherwise type enter to continue");
            var scanner = new Scanner(System.in);
            if (scanner.nextLine().equalsIgnoreCase("yes")) {
                var name = factory.createAnnotation(factory.createReference(AstElements.PROTOBUF_MESSAGE_NAME));
                name.addValue("value", oneOfStatement.className());
                possibleEnumType.addAnnotation(name);
                return createNestedEnum(enumStatement, possibleEnumType);
            }
        }
        
        log.info("Oneof statement %s in %s doesn't have an enum descriptor(expected %s)."
                .formatted(oneOfStatement.name(), oneOfStatement.parent().name(), oneOfStatement.className()));
        log.info("Type its name or click enter to generate it:");
        var suggestedNames = AstUtils.getSuggestedNames(factory.getModel(), oneOfStatement.className(), true);
        log.info("Suggested names: %s".formatted(suggestedNames));
        var scanner = new Scanner(System.in);
        var newName = scanner.nextLine();
        if (newName.isBlank()) {
            return createNestedEnum(enumStatement, null);
        }

        var result = (CtEnum<?>) AstUtils.getProtobufClass(factory.getModel(), newName, true);
        if(result != null){
            var name = factory.createAnnotation(factory.createReference(AstElements.PROTOBUF_MESSAGE_NAME));
            name.addValue("value", oneOfStatement.className());
            result.addAnnotation(name);
            return createNestedEnum(enumStatement, result);
        }

        log.info("Enum %s doesn't exist, try again".formatted(newName));
        return createOneOfEnumDescriptor(oneOfStatement, true);
    }

    private CtEnum<?> getExistingOneOfDescriptor(ProtobufEnumStatement enumStatement) {
        return ctType.getNestedTypes()
                .stream()
                .filter(CtTypeInformation::isEnum)
                .map(entry -> (CtEnum<?>) entry)
                .filter(entry -> inferExistingOneOfDescriptor(enumStatement, entry))
                .findFirst()
                .orElse(null);
    }

    private boolean inferExistingOneOfDescriptor(ProtobufEnumStatement enumStatement, CtEnum<?> entry) {
        var enumStatementEntries = enumStatement.statements()
                .stream()
                .map(ProtobufFieldStatement::name)
                .collect(Collectors.toUnmodifiableSet());
        var success = entry.getEnumValues()
                .stream()
                .filter(enumValue -> enumStatementEntries.contains(enumValue.getSimpleName()
                        .toLowerCase()
                        .replaceAll("_", "")))
                .count();
        return ((float) success / enumStatementEntries.size()) > 0.5;
    }

    private ProtobufEnumStatement createOneOfEnum(ProtobufOneOfStatement oneOfStatement) {
        var fieldsCounter = 0;
        var enumStatement = new ProtobufEnumStatement(oneOfStatement.className(), oneOfStatement.packageName(), oneOfStatement.parent());
        var defaultStatement = new ProtobufFieldStatement(
                fieldsCounter++,
                "UNKNOWN",
                enumStatement.packageName(),
                oneOfStatement.parent()
        );
        enumStatement.addStatement(defaultStatement);
        for (var fieldStatement : oneOfStatement.statements()) {
            enumStatement.addStatement(new ProtobufFieldStatement(
                    fieldsCounter++,
                    fieldStatement.nameAsConstant(),
                    enumStatement.packageName(),
                    oneOfStatement.parent()
            ));
        }
        
        return enumStatement;
    }

    private void createNestedEnumWithLookup(ProtobufEnumStatement enumStatement) {
        var existing = (CtEnum<?>) AstUtils.getProtobufClass(factory.getModel(), enumStatement.name(), true);
        createNestedEnum(enumStatement, existing);
    }

    private CtEnum<?> createNestedEnum(ProtobufEnumStatement enumStatement, CtEnum<?> existing) {
        var creator = new EnumSchemaCreator(existing, ctType, enumStatement, accessors, factory);
        var result = creator.update();
        if(existing == null) {
            result.setParent(parent);
            ctType.addNestedType(result);
        }

        return result;
    }

    private void createNestedMessageWithLookup(ProtobufMessageStatement messageStatement) {
        var existing = AstUtils.getProtobufClass(factory.getModel(), messageStatement.name(), false);
        var creator = new MessageSchemaCreator(existing, ctType, messageStatement, accessors, factory);
        var result = creator.update();
        if(existing == null) {
            result.setParent(parent);
            ctType.addNestedType(result);
        }
    }

    private CtClass<?> createClass() {
        CtClass<?> ctClass = factory.createClass(protoStatement.staticallyQualifiedName());
        ctClass.setModifiers(Set.of(ModifierKind.PUBLIC));
        if(protoStatement.nested()){
            ctClass.addModifier(ModifierKind.STATIC);
        }

        ctClass.addSuperInterface(factory.createReference(AstElements.PROTOBUF_MESSAGE));
        ctClass.addAnnotation(factory.createAnnotation(factory.createReference(AstElements.ALL_ARGS_CONSTRUCTOR)));
        if(!accessors){
            ctClass.addAnnotation(factory.createAnnotation(factory.createReference(AstElements.DATA)));
        }

        ctClass.addAnnotation(factory.createAnnotation(factory.createReference(AstElements.JACKSONIZED)));
        ctClass.addAnnotation(factory.createAnnotation(factory.createReference(AstElements.BUILDER)));
        var name = factory.createAnnotation(factory.createReference(AstElements.PROTOBUF_MESSAGE_NAME));
        name.addValue("value", protoStatement.name());
        ctClass.addAnnotation(name);

        return ctClass;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private CtFieldRead createFieldRead(ProtobufFieldStatement entry) {
        CtFieldRead fieldRead = factory.createFieldRead();
        fieldRead.setType(ctType.getReference());
        fieldRead.setVariable(getExistingField(entry).getReference());
        return fieldRead;
    }

    private CtReturn<?> createReturn(CtEnum<?> javaEnum, ProtobufFieldStatement entry) {
        return createReturn(javaEnum, entry, entry.nameAsConstant(), false);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private CtReturn createReturn(CtEnum javaEnum, ProtobufFieldStatement entry, String constantName, boolean annotate) {
        CtEnumValue constant = javaEnum.getEnumValue(constantName);
        if(constant == null){
            log.info("Enum constant %s doesn't exist inside %s".formatted(constantName, javaEnum.getSimpleName()));
            log.info("Known constants: %s".formatted(javaEnum.getEnumValues()
                    .stream()
                    .map(element -> ((CtNamedElement) element).getSimpleName())
                    .collect(Collectors.joining(", "))));
            log.info("Type the correct name or hit enter to generate it");

            var scanner = new Scanner(System.in);
            var newName = scanner.nextLine();
            if (!newName.isBlank()) {
                return createReturn(javaEnum, entry, newName, true);
            }

            var enumInitializer = factory.createConstructorCall();
            enumInitializer.addArgument(factory.createLiteral(entry.index()));
            var enumValue = factory.createEnumValue();
            enumValue.setSimpleName(entry.nameAsConstant());
            enumValue.setAssignment(enumInitializer);
            enumValue.setType(javaEnum.getReference());
            enumValue.setParent(javaEnum);
            constant = enumValue;
        }

        if(annotate) {
            var name = factory.createAnnotation(
                    factory.createReference(AstElements.PROTOBUF_MESSAGE_NAME)
            );
            name.addValue("value", entry.nameAsConstant());
            constant.addAnnotation(name);
        }

        constant.setType(javaEnum.getReference());
        CtFieldRead fieldRead = factory.createFieldRead();
        fieldRead.setType(javaEnum.getReference());
        fieldRead.setVariable(constant.getReference());
        fieldRead.setTarget(factory.Code().createTypeAccess(javaEnum.getReference()));
        return factory.createReturn()
                .setReturnedExpression(fieldRead);
    }
}

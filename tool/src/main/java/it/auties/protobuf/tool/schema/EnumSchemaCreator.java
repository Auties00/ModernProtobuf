package it.auties.protobuf.tool.schema;

import it.auties.protobuf.parser.statement.ProtobufEnumStatement;
import it.auties.protobuf.parser.statement.ProtobufFieldStatement;
import it.auties.protobuf.tool.util.AstElements;
import it.auties.protobuf.tool.util.AstUtils;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtReturn;
import spoon.reflect.declaration.CtEnum;
import spoon.reflect.declaration.CtEnumValue;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class EnumSchemaCreator extends SchemaCreator<CtEnum<?>, ProtobufEnumStatement> {
    public EnumSchemaCreator(ProtobufEnumStatement protoStatement, boolean accessors, Factory factory) {
        super(protoStatement, accessors, factory);
    }

    public EnumSchemaCreator(CtEnum<?> ctType, ProtobufEnumStatement protoStatement, boolean accessors) {
        super(ctType, protoStatement, accessors);
    }

    public EnumSchemaCreator(CtEnum<?> ctType, CtType<?> parent, ProtobufEnumStatement protoStatement, boolean accessors) {
        super(ctType, parent, protoStatement, accessors);
    }

    @Override
    public CtEnum<?> generate() {
        this.ctType = createEnumClass();
        createEnum();
        return ctType;
    }

    @Override
    public CtEnum<?> update(boolean force) {
        if(ctType == null){
            if(!force){
                log.info("Schema %s doesn't have a model".formatted(protoStatement.name()));
                log.info("Type its name if it already exists, ignored if you want to skip it or click enter to generate a new one");
                log.info("Suggested names: %s".formatted(
                    AstUtils.getSuggestedNames(factory.getModel(), protoStatement.name(), true)));
                var scanner = new Scanner(System.in);
                var newName = scanner.nextLine();
                if(newName.equals("ignored")){
                    return ctType;
                }

                if (!newName.isBlank()) {
                    this.ctType = (CtEnum<?>) AstUtils.getProtobufClass(factory.getModel(), newName, true);
                    this.updating = ctType != null;
                    return update(false);
                }
            }

            this.ctType = createEnumClass();
        }

        createEnum();
        return ctType;
    }

    private void createEnum() {
        createEnumValues();
        var indexField = addIndexField();
        if(!updating || !hasAllArgsConstructor()) {
            ctType.addAnnotation(
                    factory.createAnnotation(factory.createReference(AstElements.ALL_ARGS_CONSTRUCTOR))
            );
        }

        createNamedConstructor(indexField);
    }

    private boolean hasAllArgsConstructor() {
        return ctType.getAnnotations()
                .stream()
                .anyMatch(entry -> Objects.equals(entry.getName(), "AllArgsConstructor"));
    }

    private boolean hasGetter(CtField<?> field) {
        return field.getAnnotations()
                .stream()
                .anyMatch(entry -> Objects.equals(entry.getName(), "Getter"));
    }

    private void createNamedConstructor(CtField<?> indexField) {
        var existing = ctType.getMethodsByName("of");
        if(existing != null){
            return;
        }

        CtMethod<?> method = factory.createMethod(
                ctType,
                Set.of(ModifierKind.PUBLIC, ModifierKind.STATIC),
                ctType.getReference(),
                "of",
                List.of(),
                Set.of()
        );
        factory.createParameter(
                method,
                factory.Type().integerPrimitiveType(),
                "index"
        );

        method.addAnnotation(factory.createAnnotation(factory.createReference(AstElements.JSON_CREATOR)));
        var body = factory.createBlock();

        var valuesMethod = factory.Method().createReference(
                ctType.getReference(),
                factory.Type().createArrayReference(ctType.getReference()),
                "values"
        );
        var valuesInvocation = factory.createInvocation(
                factory.createTypeAccess(ctType.getReference()),
                valuesMethod
        );

        var streamMethod = factory.Method().createReference(
                factory.createReference(AstElements.ARRAYS),
                factory.Type().createArrayReference(ctType.getReference()),
                "stream",
                factory.createArrayTypeReference()
        );
        var streamInvocation = factory.createInvocation(
                factory.createTypeAccess(factory.createReference(AstElements.ARRAYS)),
                streamMethod,
                valuesInvocation
        );

        var filterMethod = factory.Method().createReference(
                factory.createReference(AstElements.STREAM),
                factory.createReference(AstElements.STREAM),
                "filter",
                factory.createReference(AstElements.PREDICATE)
        );
        var predicateLambda = factory.createLambda();
        var predicateLambdaParameter = factory.createParameter(
                predicateLambda,
                null,
                "entry"
        );

        var indexMethod = factory.Method().createReference(
                ctType.getReference(),
                factory.Type().integerPrimitiveType(),
                "index"
        );
        var indexInvocation = factory.createInvocation(
                factory.createVariableRead(factory.createParameterReference(predicateLambdaParameter), false),
                indexMethod
        );
        var indexRead = createFieldRead(factory, indexField);
        var indexCheck = factory.createBinaryOperator(
                indexInvocation,
                indexRead,
                BinaryOperatorKind.EQ
        );

        predicateLambda.setExpression(indexCheck);
        var filterInvocation = factory.createInvocation(
                streamInvocation,
                filterMethod,
                predicateLambda
        );

        var findFirstMethod = factory.Method().createReference(
                factory.createReference(AstElements.STREAM),
                factory.createReference(AstElements.STREAM),
                "findFirst"
        );
        var findFirstInvocation = factory.createInvocation(
                filterInvocation,
                findFirstMethod
        );

        var orElseMethod = factory.Method().createReference(
                factory.createReference(AstElements.STREAM),
                factory.createReference(AstElements.STREAM),
                "orElse",
                factory.createReference(AstElements.OBJECT)
        );
        var orElseInvocation = factory.createInvocation(
                findFirstInvocation,
                orElseMethod,
                factory.createLiteral(null)
        );
        var returnStatement = factory.createReturn();
        returnStatement.setReturnedExpression(orElseInvocation);
        body.addStatement(returnStatement);
        method.setBody(body);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private CtFieldRead<?> createFieldRead(Factory factory, CtField<?> indexField) {
        CtFieldRead indexRead = factory.createFieldRead();
        indexRead.setVariable(indexField.getReference());
        return indexRead;
    }

    private CtField<?> addIndexField() {
        var existingField = Optional.ofNullable(ctType.getDeclaredOrInheritedField("index"))
                .<CtField<?>>map(CtFieldReference::getFieldDeclaration)
                .orElseGet(this::createIndexField);
        if(accessors) {
            var existingAccessor = ctType.getMethod("index");
            if (existingAccessor == null && !hasGetter(existingField)) {
                createIndexAccessor(existingField);
            }
        }

        return existingField;
    }

    private CtField<?> createIndexField() {
        var field = factory.createField(
                ctType,
                Set.of(ModifierKind.PRIVATE, ModifierKind.FINAL),
                factory.Type().integerPrimitiveType(),
                "index"
        );
        if(!accessors){
            field.addAnnotation(factory.createAnnotation(factory.createReference(AstElements.GETTER)));
        }

        return field;
    }

    @SuppressWarnings({"unchecked", "rawtypes", "UnusedReturnValue"})
    private CtMethod<?> createIndexAccessor(CtField<?> indexField) {
        var accessor = factory.createMethod(
                ctType,
                Set.of(ModifierKind.PUBLIC),
                indexField.getType(),
                indexField.getSimpleName(),
                List.of(),
                Set.of()
        );

        var body = factory.createBlock();
        accessor.setBody(body);
        CtReturn returnStatement = factory.createReturn();
        CtFieldRead fieldRead = factory.createFieldRead();
        fieldRead.setType(ctType.getReference());
        fieldRead.setVariable(indexField.getReference());
        returnStatement.setReturnedExpression(fieldRead);
        body.addStatement(returnStatement);
        return accessor;
    }

    private CtEnum<?> createEnumClass() {
        var enumClass = factory.createEnum(protoStatement.staticallyQualifiedName());
        enumClass.setModifiers(Set.of(ModifierKind.PUBLIC));
        enumClass.addSuperInterface(factory.Type().createReference(AstElements.PROTOBUF_MESSAGE));
        AstUtils.addProtobufName(enumClass, protoStatement.name());
        if(parent != null) {
            enumClass.setParent(parent);
            parent.addNestedType(enumClass);
        }

        return enumClass;
    }

    private void createEnumValues() {
        protoStatement.statements()
                .stream()
                .filter(entry -> getEnumValue(entry) == null)
                .map(entry -> createEnumValue(entry, factory))
                .forEach(ctType::addEnumValue);
    }

    private CtEnumValue<?> getEnumValue(ProtobufFieldStatement fieldStatement){
        return ctType.filterChildren(new TypeFilter<>(CtEnumValue.class))
                .filterChildren((CtEnumValue<?> entry) -> AstUtils.hasFieldEnumIndex(fieldStatement, entry))
                .first(CtEnumValue.class);
    }

    private CtEnumValue<Object> createEnumValue(ProtobufFieldStatement entry, Factory factory) {
        log.info("Creating enum value(%s) inside %s"
            .formatted(entry, entry.parent().name()));
        var enumInitializer = factory.createConstructorCall();
        enumInitializer.addArgument(factory.createLiteral(entry.index()));
        var enumValue = factory.createEnumValue();
        enumValue.setSimpleName(entry.nameAsConstant());
        enumValue.setAssignment(enumInitializer);
        return enumValue;
    }
}

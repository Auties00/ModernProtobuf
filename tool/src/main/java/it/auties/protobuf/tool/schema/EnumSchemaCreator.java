package it.auties.protobuf.tool.schema;

import it.auties.protobuf.parser.statement.ProtobufEnumStatement;
import it.auties.protobuf.parser.statement.ProtobufFieldStatement;
import it.auties.protobuf.parser.statement.ProtobufStatementType;
import it.auties.protobuf.tool.util.AstElements;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public class EnumSchemaCreator extends SchemaCreator<CtEnum<?>, ProtobufEnumStatement> {
    public EnumSchemaCreator(CtEnum<?> ctType, ProtobufEnumStatement protoStatement, Factory factory) {
        super(ctType, protoStatement, factory);
    }

    public EnumSchemaCreator(ProtobufEnumStatement protoStatement, Factory factory) {
        super(protoStatement, factory);
    }

    public EnumSchemaCreator(CtEnum<?> ctType, CtType<?> parent, ProtobufEnumStatement protoStatement, Factory factory) {
        super(ctType, parent, protoStatement, factory);
    }

    @Override
    public CtEnum<?> createSchema() {
        this.ctType = createEnumClass();
        createEnumValues(true);
        var indexField = addIndexField(true);
        createEnumConstructor(indexField);
        createNamedConstructor(indexField, true);
        return ctType;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void createEnumConstructor(CtField<?> ctField) {
        var constructor = factory.createConstructor(
                ctType,
                Set.of(),
                List.of(),
                Set.of(),
                factory.createBlock()
        );

        var parameter =  factory.createParameter(
                constructor,
                ctField.getType(),
                ctField.getSimpleName()
        );

        CtFieldRead localFieldRead = factory.createFieldRead();
        localFieldRead.setTarget(factory.createThisAccess(ctType.getReference()));
        localFieldRead.setVariable(
                factory.Field().createReference(
                        ctType.getReference(),
                        ctField.getType(),
                        ctField.getSimpleName()
                )
        );

        CtVariableRead parameterRead = factory.createVariableRead();
        parameterRead.setVariable(parameter.getReference());

        CtAssignment assignment = factory.createAssignment();
        assignment.setAssigned(localFieldRead);
        assignment.setAssignment(parameterRead);

        constructor.getBody().addStatement(assignment);
    }

    @Override
    public CtEnum<?> update() {
        Objects.requireNonNull(ctType, "Cannot update type without it");
        createEnumValues(false);
        var indexField = addIndexField(false);
        createNamedConstructor(indexField, false);
        return ctType;
    }

    private void createNamedConstructor(CtField<?> indexField, boolean force) {
        if(!force){
            var existing = getNamedConstructor();
            if(existing != null){
                return;
            }
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

    private List<CtMethod<?>> getNamedConstructor() {
        return ctType.getMethodsByName("of");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private CtFieldRead<?> createFieldRead(Factory factory, CtField<?> indexField) {
        CtFieldRead indexRead = factory.createFieldRead();
        indexRead.setVariable(indexField.getReference());
        return indexRead;
    }

    private CtField<?> addIndexField(boolean force) {
        var existingField = ctType.getField("index");
        var existingAccessor = ctType.getMethod("index");
        if(!force && existingField != null && existingAccessor != null){
            return existingField;
        }

        if(existingField == null) {
            existingField = createIndexField();
        }

        if(existingAccessor == null){
            createIndexAccessor(existingField);
        }

        return existingField;
    }

    private CtField<?> createIndexField() {
        return factory.createField(
                ctType,
                Set.of(ModifierKind.PRIVATE, ModifierKind.FINAL),
                factory.Type().integerPrimitiveType(),
                "index"
        );
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
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
        var enumClass = factory.createEnum(protoStatement.qualifiedName());
        if(protoStatement.nested()) {
            Objects.requireNonNull(parent, "Missing parent during AST generation");
            enumClass.setParent(parent);
        }

        enumClass.setModifiers(Set.of(ModifierKind.PUBLIC));
        enumClass.addSuperInterface(factory.Type().createReference(AstElements.PROTOBUF_MESSAGE));
        var name = factory.createAnnotation(factory.createReference(AstElements.PROTOBUF_MESSAGE_NAME));
        name.addValue("value", protoStatement.name());
        return enumClass;
    }

    private void createEnumValues(boolean force) {
        protoStatement.statements()
                .stream()
                .filter(entry -> force || getEnumValue(entry) == null)
                .map(entry -> createEnumValue(entry, factory))
                .forEach(ctType::addEnumValue);
    }

    private CtEnumValue<?> getEnumValue(ProtobufFieldStatement fieldStatement){
        return ctType.filterChildren(new TypeFilter<>(CtEnumValue.class))
                .filterChildren((CtEnumValue<?> entry) -> hasFieldEnumIndex(fieldStatement, entry))
                .first(CtEnumValue.class);
    }

    // Assume to be first in position
    private boolean hasFieldEnumIndex(ProtobufFieldStatement fieldStatement, CtField<?> element) {
        var expression = element.getDefaultExpression();
        if (!(expression instanceof CtConstructorCall<?> constructor)) {
            return false;
        }

        var arguments = constructor.getArguments();
        if (arguments.isEmpty()) {
            return false;
        }

        var assumedIndex = arguments.get(0);
        if (!(assumedIndex instanceof CtLiteral<?> literal)) {
            return false;
        }

        return fieldStatement.index() == literal.getValue();
    }

    private CtEnumValue<Object> createEnumValue(ProtobufFieldStatement entry, Factory factory) {
        var enumInitializer = factory.createConstructorCall();
        enumInitializer.addArgument(factory.createLiteral(entry.index()));
        var enumValue = factory.createEnumValue();
        enumValue.setSimpleName(entry.nameAsConstant());
        enumValue.setAssignment(enumInitializer);
        return enumValue;
    }
}

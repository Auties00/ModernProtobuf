package it.auties.protobuf.tool.util;

import static it.auties.protobuf.parser.statement.ProtobufStatementType.ENUM;
import static it.auties.protobuf.parser.statement.ProtobufStatementType.MESSAGE;

import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufName;
import it.auties.protobuf.parser.statement.ProtobufFieldStatement;
import it.auties.protobuf.parser.type.ProtobufMessageType;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

@UtilityClass
public class AstUtils implements LogProvider {
    public Launcher createLauncher() {
        var launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(18);
        launcher.getEnvironment().setPreviewFeaturesEnabled(true);
        launcher.addInputResource("C:\\Users\\alaut\\ProtocCompiler\\base\\src\\main\\java");
        launcher.getEnvironment().setAutoImports(true);
        return launcher;
    }

    public CtClass<?> getProtobufClass(CtModel model, String name, boolean enumType) {
        return model.getElements(new TypeFilter<>(CtClass.class))
            .stream()
            .filter(element -> isProtobufMessage(element, enumType))
            .filter(element -> {
                var annotation = element.getAnnotation(ProtobufName.class);
                return Objects.equals(element.getSimpleName(), name)
                    || (annotation != null && Objects.equals(annotation.value(), name));
            })
            .findFirst()
            .orElse(null);
    }

    public boolean isProtobufMessage(CtType<?> element, boolean isEnum){
        if(element.isEnum()){
            return true;
        }

        var entries = getAllSuperClasses(element);
        return element.isEnum() == isEnum
            && entries.stream().anyMatch(entry -> entry.getSimpleName().equals(ProtobufMessage.class.getSimpleName()));
    }

    public Set<CtTypeReference<?>> getAllSuperClasses(CtType<?> element){
        var entries = new HashSet<CtTypeReference<?>>();
        if(element == null){
            return entries;
        }

        entries.add(element.getReference());
        if(element.getSuperclass() != null) {
            entries.add(element.getSuperclass());
            entries.addAll(getAllSuperClasses(element.getSuperclass().getTypeDeclaration()));
        }

        entries.addAll(element.getSuperInterfaces());
        element.getSuperInterfaces()
            .stream()
            .map(CtTypeReference::getTypeDeclaration)
            .map(AstUtils::getAllSuperClasses)
            .forEach(entries::addAll);
        return entries;
    }

    public CtTypeReference<?> createReference(ProtobufFieldStatement statement, boolean generic, Factory factory){
        if(generic && statement.repeated()){
            var listReference = factory.Type().createReference(List.class);
            listReference.addActualTypeArgument(createReference(statement, false, factory));
            return listReference;
        }

        return switch (statement.type().type()){
            case MESSAGE -> {
                var reference = (ProtobufMessageType) statement.type();
                var knownType = getProtobufClass(factory.getModel(), reference.name(), reference.declaration().statementType() == ENUM);
                if (knownType == null && statement.parent().statementType() == MESSAGE) {
                    yield factory.createReference(reference.name());
                }

                if (knownType != null) {
                    yield knownType.getReference();
                }

                var annotatedType = getProtobufClass(factory.getModel(), reference.name(), false);
                if(annotatedType != null){
                    yield annotatedType.getReference();
                }

                yield factory.createReference(reference.declaration().qualifiedName());
            }
            case FLOAT -> statement.required() ? factory.Type().floatPrimitiveType() : factory.Type().floatType();
            case DOUBLE -> statement.required() ? factory.Type().doublePrimitiveType() : factory.Type().doubleType();
            case BOOL -> statement.required() ? factory.Type().booleanPrimitiveType() : factory.Type().booleanType();
            case STRING -> factory.Type().stringType();
            case BYTES -> factory.createArrayReference(factory.Type().bytePrimitiveType());
            case INT32, UINT32, SINT32, FIXED32, SFIXED32 -> statement.required() ? factory.Type().integerPrimitiveType() : factory.Type().integerType();
            case INT64, SINT64, UINT64, FIXED64, SFIXED64 -> statement.required() ? factory.Type().longPrimitiveType() : factory.Type().longType();
        };
    }

    public Object getSuggestedNames(CtModel model, String originalName, boolean enumType) {
        var elements = model.getElements(new TypeFilter<>(CtClass.class))
            .stream()
            .filter(ctType -> isProtobufMessage(ctType, enumType))
            .toList();
        return getSuggestedNames(originalName, elements);
    }

    @SuppressWarnings("rawtypes")
    private String getSuggestedNames(String originalName, List<CtClass> result) {
        return result.stream()
            .map(entry -> getClassName(entry, originalName))
            .filter(SimilarString::isSuggestion)
            .sorted()
            .map(SimilarString::toString)
            .collect(Collectors.joining(", "));
    }

    private SimilarString getClassName(CtClass<?> ctClass, String comparable) {
        try {
            var annotation = ctClass.getAnnotation(ProtobufName.class);
            if (annotation != null) {
                return new SimilarString(comparable, annotation.value(), ctClass.getSimpleName());
            }
        }catch (Throwable ignored){

        }

        return new SimilarString(comparable, ctClass.getSimpleName(), null);
    }

    private record SimilarString(String name, String oldName, double similarity) implements Comparable<SimilarString> {
        private SimilarString(String comparable, String name, String oldName){
            this(name, oldName, StringUtils.similarity(comparable, name));
        }

        public boolean isSuggestion(){
            return similarity > 0.5;
        }

        @Override
        public String toString() {
            return oldName == null ? name
                : "%s(java name: %s)".formatted(name, oldName);
        }

        @Override
        public int compareTo(SimilarString o) {
            return Double.compare(o.similarity(), similarity());
        }
    }

    public boolean hasFieldEnumIndex(ProtobufFieldStatement fieldStatement, CtField<?> element) {
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

        var value = literal.getValue();
        return value instanceof Number number
            && fieldStatement.index() == number.intValue();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void addProtobufName(CtType<?> ctClass, String name){
        CtTypeReference reference = ctClass.getFactory()
            .createReference(AstElements.PROTOBUF_MESSAGE_NAME);
        var annotation = ctClass.getAnnotation(reference);
        if (annotation != null) {
            annotation.setElementValues(Map.of("value", name));
            return;
        }

        var newAnnotation = ctClass.getFactory().createAnnotation(reference);
        ctClass.addAnnotation(newAnnotation);
        newAnnotation.setElementValues(Map.of("value", name));
    }

    public String getProtobufName(CtType<?> type){
        return Optional.of(type)
            .map(entry -> entry.getAnnotation(ProtobufName.class))
            .map(ProtobufName::value)
            .orElseGet(type::getSimpleName);
    }
}

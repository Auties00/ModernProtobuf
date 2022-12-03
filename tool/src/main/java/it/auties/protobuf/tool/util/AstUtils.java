package it.auties.protobuf.tool.util;

import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufMessageName;
import it.auties.protobuf.parser.statement.ProtobufFieldStatement;
import it.auties.protobuf.parser.type.ProtobufMessageType;
import lombok.experimental.UtilityClass;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.chain.CtQuery;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static it.auties.protobuf.parser.statement.ProtobufStatementType.ENUM;
import static it.auties.protobuf.parser.statement.ProtobufStatementType.MESSAGE;

@UtilityClass
public class AstUtils implements LogProvider {
    public Launcher createLauncher() {
        var launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.getEnvironment().setPreviewFeaturesEnabled(true);
        launcher.addInputResource("C:\\Users\\alaut\\ProtocCompiler\\base\\src\\main\\java");
        launcher.getEnvironment().setAutoImports(true);
        return launcher;
    }

    public CtClass<?> getBuilderClass(CtType<?> type){
        return type.filterChildren(new TypeFilter<>(CtClass.class))
                .filterChildren((CtClass<?> element) -> element.isClass() && element.getSimpleName().equals("%sBuilder".formatted(type.getSimpleName())))
                .first(CtClass.class);
    }

    public CtClass<?> getProtobufClass(CtModel model, String name, boolean enumType) {
        return getProtobufClass(model.filterChildren(new TypeFilter<>(CtClass.class)), name, enumType);
    }

    public CtClass<?> getProtobufClass(CtType<?> type, String name, boolean enumType){
        return getProtobufClass(type.filterChildren(new TypeFilter<>(CtClass.class)), name, enumType);
    }

    private CtClass<?> getProtobufClass(CtQuery query, String name, boolean enumType) {
        return query.filterChildren((CtClass<?> element) -> enumType ? element.isEnum() : isProtobufMessage(element))
                .filterChildren((CtClass<?> element) -> hasClassName(name, element))
                .first(CtClass.class);
    }

    private boolean hasClassName(String name, CtClass<?> element) {
        try {
            var annotation = element.getAnnotation(ProtobufMessageName.class);
            if(annotation != null && annotation.value() != null){
                return annotation.value().equals(name);
            }
        }catch (Throwable throwable){
            // Ignored
        }

        return element.getSimpleName().equals(name);
    }

    public boolean isProtobufMessage(CtType<?> element){
        if(element.isEnum()){
            return true;
        }

        var entries = new HashSet<CtTypeReference<?>>();
        if(element.getSuperclass() != null) {
            entries.add(element.getSuperclass());
        }

        entries.addAll(element.getSuperInterfaces());
        return hasProtobufMessage(entries) || hasProtobufMessageDeep(entries);
    }

    public boolean hasProtobufMessageDeep(Set<CtTypeReference<?>> entries) {
        return entries.stream()
                .map(CtTypeReference::getTypeDeclaration)
                .filter(Objects::nonNull)
                .anyMatch(AstUtils::isProtobufMessage);
    }

    public boolean hasProtobufMessage(Set<CtTypeReference<?>> entries) {
        return entries.stream()
                .anyMatch(entry -> entry.getSimpleName().equals(ProtobufMessage.class.getSimpleName()));
    }

    public CtTypeReference<?> createReference(ProtobufFieldStatement statement, boolean generic, Factory factory){
        if(generic && statement.repeated()){
            var listReference = factory.Type().createReference(List.class);
            listReference.addActualTypeArgument(createReference(statement, false, factory));
            return listReference;
        }

        return switch (statement.reference().type()){
            case MESSAGE -> {
                var reference = (ProtobufMessageType) statement.reference();
                var knownType = getProtobufClass(factory.getModel(), reference.name(), reference.declaration().type() == ENUM);
                if (knownType == null && statement.parent().type() == MESSAGE) {
                    yield factory.createReference(reference.name());
                }

                if (knownType != null) {
                    yield knownType.getReference();
                }

                var annotatedType = factory.getModel()
                        .filterChildren(new TypeFilter<>(CtClass.class))
                        .filterChildren((CtClass<?> entry) -> hasClassName(reference.name(), entry))
                        .first(CtClass.class);
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

    public Object getSuggestedNames(CtClass<?> ctType, String originalName, boolean enumType) {
        var elements = ctType.getElements(new TypeFilter<>(CtClass.class))
                .stream()
                .filter(ctEntry -> ctEntry.isEnum() == enumType)
                .toList();
        return getSuggestedNames(originalName, elements);
    }

    public Object getSuggestedNames(CtModel model, String originalName, boolean enumType) {
        var elements = model.getElements(new TypeFilter<>(CtClass.class))
                .stream()
                .filter(ctType -> ctType.isEnum() == enumType)
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
        var annotation = ctClass.getAnnotation(ProtobufMessageName.class);
        return annotation == null ? new SimilarString(comparable, ctClass.getSimpleName(), null)
                : new SimilarString(comparable, annotation.value(), ctClass.getSimpleName());
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
}

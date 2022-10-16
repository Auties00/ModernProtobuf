package it.auties.protobuf.tool.util;

import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufMessageName;
import it.auties.protobuf.base.ProtobufProperty;
import it.auties.protobuf.base.ProtobufType;
import it.auties.protobuf.parser.statement.ProtobufObject;
import it.auties.protobuf.parser.statement.ProtobufFieldStatement;
import it.auties.protobuf.parser.statement.ProtobufMessageStatement;
import lombok.experimental.UtilityClass;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.chain.CtQuery;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

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
        launcher.buildModel();
        return launcher;
    }

    public CtClass<?> getBuilderClass(CtType<?> type){
        return type.filterChildren(new TypeFilter<>(CtClass.class))
                .filterChildren((CtClass<?> element) -> element.isClass() && element.getSimpleName().equals("%sBuilder".formatted(type.getSimpleName())))
                .first(CtClass.class);
    }

    public CtClass<?> getProtobufClass(CtModel model, ProtobufObject<?> object){
        return getProtobufClass(model.filterChildren(new TypeFilter<>(CtClass.class)), object);
    }

    public CtClass<?> getProtobufClass(CtType<?> type, ProtobufObject<?> object){
        return getProtobufClass(type.filterChildren(new TypeFilter<>(CtClass.class)), object);
    }

    private CtClass<?> getProtobufClass(CtQuery query, ProtobufObject<?> object) {
        return query.filterChildren((CtClass<?> element) -> isContainingClass(object, element))
                .filterChildren((CtClass<?> element) -> hasClassName(object, element))
                .first(CtClass.class);
    }

    private boolean isContainingClass(ProtobufObject<?> statement, CtClass<?> element) {
        return statement instanceof ProtobufMessageStatement ?
                element.isClass() && AstUtils.isProtobufMessage(element) : element.isEnum();
    }

    private boolean hasClassName(ProtobufObject<?> statement, CtClass<?> element) {
        return hasClassAnnotationName(element)
                || element.getSimpleName().equals(statement.name());
    }

    private boolean hasClassAnnotationName(CtClass<?> element){
        try {
            var annotation = element.getAnnotation(ProtobufMessageName.class);
            return annotation != null
                    && annotation.value() != null
                    && annotation.value().equals(element.getSimpleName());
        }catch (Throwable throwable){
            return false;
        }
    }
    public void check(CtType<?> owner, CtField<?> field, ProtobufFieldStatement statement) {
        var annotation = field.getAnnotations()
                .stream()
                .filter(entry -> ProtobufProperty.class.isAssignableFrom(entry.getActualAnnotation().getClass()))
                .findFirst()
                .orElse(null);
        if(annotation == null){
            return;
        }

        var name = getName(field, statement);
        var index = annotation.getValueAsInt("index");
        if(index != statement.index()){
            log.warn("Wrong index for field {} in {}: expected {}, got {}",
                    name, owner.getSimpleName(), statement.index(), index);
            annotation.addValue("index", statement.index());
        }

        var required = (boolean) annotation.getValueAsObject("required");
        if(required != statement.required()){
            log.warn("Erroneous required flag for field {} in {}: expected {}, got {}",
                    name, owner.getSimpleName(), statement.required(), required);
            annotation.addValue("index", statement.index());
        }

        var repeated = (boolean) annotation.getValueAsObject("repeated");
        if(repeated != statement.repeated()){
            log.warn("Erroneous repeated flag for field {} in {}: expected {}, got {}",
                    name, owner.getSimpleName(), statement.repeated(), repeated);
            annotation.addValue("repeated", statement.repeated());
        }

        var packed = (boolean) annotation.getValueAsObject("packed");
        if(packed != statement.packed()){
            log.warn("Erroneous packed flag for field {} in {}: expected {}, got {}",
                    name, owner.getSimpleName(), statement.packed(), packed);
            annotation.addValue("packed", statement.packed());
        }

        var fieldType = (ProtobufType) annotation.getValueAsObject("type");
        if(fieldType != statement.reference().type()){
            log.warn("Erroneous type for field {} in {}: expected {}, got {}",
                    name, owner.getSimpleName(), statement.reference().type(), fieldType);
            annotation.addValue("type", statement.reference().type());
        }
    }

    private String getName(CtField<?> ctField, ProtobufFieldStatement fieldStatement) {
        if(ctField == null){
            return getName(fieldStatement);
        }

        var property = ctField.getAnnotation(ProtobufProperty.class);
        return hasCustomName(property) ? property.name() : new ProtobufFieldStatement(ctField.getSimpleName(), null).name();
    }

    private static String getName(ProtobufFieldStatement fieldStatement) {
        return fieldStatement.parent().type() == ENUM ? fieldStatement.nameAsConstant()
                : fieldStatement.name();
    }

    private boolean hasCustomName(ProtobufProperty property) {
        return property != null
                && property.name() != null
                && !property.name().equals(ProtobufProperty.DEFAULT_NAME);
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
            var listReference = factory.createReference(AstElements.LIST);
            listReference.addActualTypeArgument(createReference(statement, false, factory));
            return listReference;
        }

        return switch (statement.reference().type()){
            case MESSAGE -> factory.createReference(statement.qualifiedType());
            case FLOAT -> statement.required() ? factory.Type().floatPrimitiveType() : factory.Type().floatType();
            case DOUBLE -> statement.required() ? factory.Type().doublePrimitiveType() : factory.Type().doubleType();
            case BOOL -> statement.required() ? factory.Type().booleanPrimitiveType() : factory.Type().booleanType();
            case STRING -> factory.Type().stringType();
            case BYTES -> factory.createArrayReference(factory.Type().bytePrimitiveType());
            case INT32, UINT32, SINT32, FIXED32, SFIXED32 -> statement.required() ? factory.Type().integerPrimitiveType() : factory.Type().integerType();
            case INT64, SINT64, UINT64, FIXED64, SFIXED64 -> statement.required() ? factory.Type().longPrimitiveType() : factory.Type().longType();
        };
    }
}

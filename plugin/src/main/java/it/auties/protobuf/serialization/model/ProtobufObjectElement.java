package it.auties.protobuf.serialization.model;

import it.auties.protobuf.annotation.ProtobufProperty;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.*;

public class ProtobufObjectElement {
    private final Type type;
    private final TypeElement typeElement;
    private final Map<Long, ProtobufPropertyElement> properties;
    private final List<ProtobufBuilderElement> builders;
    private final Map<Integer, String> constants;
    private final ProtobufEnumMetadata enumMetadata;
    private final ProtobufConverterMethod deserializer;
    private final Set<? extends ProtobufReservedElement> reservedElements;
    private ProtobufUnknownFieldsElement unknownFieldsElement;

    public static ProtobufObjectElement ofEnum(TypeElement typeElement, ProtobufEnumMetadata enumMetadata, Set<? extends ProtobufReservedElement> reserved) {
        return new ProtobufObjectElement(Type.ENUM, typeElement, enumMetadata, null, reserved);
    }

    public static ProtobufObjectElement ofMessage(TypeElement typeElement, ProtobufConverterMethod deserializer, Set<? extends ProtobufReservedElement> reserved) {
        return new ProtobufObjectElement(Type.MESSAGE, typeElement, null, deserializer, reserved);
    }

    public static ProtobufObjectElement ofGroup(TypeElement typeElement, ProtobufConverterMethod deserializer, Set<? extends ProtobufReservedElement> reserved) {
        return new ProtobufObjectElement(Type.GROUP, typeElement, null, deserializer, reserved);
    }

    private ProtobufObjectElement(
            Type type,
            TypeElement typeElement,
            ProtobufEnumMetadata enumMetadata,
            ProtobufConverterMethod deserializer,
            Set<? extends ProtobufReservedElement> reservedElements
    ) {
        this.type = type;
        this.typeElement = typeElement;
        this.enumMetadata = enumMetadata;
        this.deserializer = deserializer;
        this.reservedElements = reservedElements;
        this.builders = new ArrayList<>();
        this.properties = new LinkedHashMap<>();
        this.constants = new LinkedHashMap<>();
    }

    public Type type() {
        return type;
    }

    public TypeElement typeElement() {
        return typeElement;
    }

    public Optional<ProtobufEnumMetadata> enumMetadata() {
        return Optional.of(enumMetadata);
    }

    public List<ProtobufPropertyElement> properties() {
        return List.copyOf(properties.values());
    }

    public Map<Integer, String> constants() {
        return Collections.unmodifiableMap(constants);
    }

    public Optional<String> addEnumConstant(int fieldIndex, String fieldName) {
        return Optional.ofNullable(constants.put(fieldIndex, fieldName));
    }

    public Optional<ProtobufPropertyElement> addProperty(Element element, Element accessor, ProtobufPropertyType type, ProtobufProperty property) {
        var fieldName = element.getSimpleName().toString();
        var result = new ProtobufPropertyElement(
                property.index(),
                fieldName,
                accessor,
                type,
                property.required(),
                property.packed(),
                element instanceof ExecutableElement
        );
        return Optional.ofNullable(properties.put(property.index(), result));
    }

    public void addBuilder(String className, List<? extends VariableElement> parameters, ExecutableElement executableElement) {
        var builderElement = new ProtobufBuilderElement(className, parameters, executableElement);
        builders.add(builderElement);
    }

    public List<ProtobufBuilderElement> builders() {
        return Collections.unmodifiableList(builders);
    }

    public Optional<ProtobufConverterMethod> deserializer() {
        return Optional.ofNullable(deserializer);
    }

    public Optional<ProtobufUnknownFieldsElement> unknownFieldsElement() {
        return Optional.ofNullable(unknownFieldsElement);
    }

    public void setUnknownFieldsElement(ProtobufUnknownFieldsElement unknownFieldsElement) {
        this.unknownFieldsElement = unknownFieldsElement;
    }

    public Set<? extends ProtobufReservedElement> reservedElements() {
        return Collections.unmodifiableSet(reservedElements);
    }

    public boolean isIndexAllowed(long value) {
        return reservedElements.stream()
                .filter(element -> element instanceof ProtobufReservedElement.Index)
                .allMatch(element -> ((ProtobufReservedElement.Index) element).allows(value));
    }

    public boolean isNameAllowed(String name) {
        return reservedElements.stream()
                .filter(element -> element instanceof ProtobufReservedElement.Name)
                .allMatch(element -> ((ProtobufReservedElement.Name) element).allows(name));

    }

    public enum Type {
        MESSAGE,
        ENUM,
        GROUP
    }
}

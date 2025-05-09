package it.auties.protobuf.serialization.model.object;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.serialization.model.converter.ProtobufConverterMethod;
import it.auties.protobuf.serialization.model.property.ProtobufPropertyElement;
import it.auties.protobuf.serialization.model.property.ProtobufPropertyType;
import it.auties.protobuf.serialization.support.Reserved;

import javax.lang.model.element.*;
import java.util.*;

public class ProtobufObjectElement {
    private final ProtobufObjectType type;
    private final TypeElement typeElement;
    private final Map<Integer, ProtobufPropertyElement> properties;
    private final List<ProtobufBuilderElement> builders;
    private final Map<Integer, String> constants;
    private final ProtobufEnumMetadata enumMetadata;
    private final ProtobufConverterMethod serializer;
    private final ProtobufConverterMethod deserializer;
    private final Set<String> reservedNames;
    private final Set<? extends ProtobufReservedIndex> reservedIndexes;
    private ProtobufUnknownFieldsElement unknownFieldsElement;

    public static ProtobufObjectElement ofEnum(TypeElement typeElement, ProtobufEnumMetadata enumMetadata) {
        return new ProtobufObjectElement(ProtobufObjectType.ENUM, typeElement, enumMetadata, null, null);
    }

    public static ProtobufObjectElement ofMessage(TypeElement typeElement, ProtobufConverterMethod deserializer) {
        return new ProtobufObjectElement(ProtobufObjectType.MESSAGE, typeElement, null, null, deserializer);
    }

    public static ProtobufObjectElement ofGroup(TypeElement typeElement, ProtobufConverterMethod deserializer) {
        return new ProtobufObjectElement(ProtobufObjectType.GROUP, typeElement, null, null, deserializer);
    }

    public static ProtobufObjectElement ofSynthetic(TypeElement typeElement, ProtobufConverterMethod serializer, ProtobufConverterMethod deserializer) {
        return new ProtobufObjectElement(ProtobufObjectType.SYNTHETIC, typeElement, null, serializer, deserializer);
    }

    private ProtobufObjectElement(ProtobufObjectType type, TypeElement typeElement, ProtobufEnumMetadata enumMetadata, ProtobufConverterMethod serializer, ProtobufConverterMethod deserializer) {
        this.type = type;
        this.typeElement = typeElement;
        this.enumMetadata = enumMetadata;
        this.serializer = serializer;
        this.deserializer = deserializer;
        this.reservedNames = Reserved.getNames(this);
        this.reservedIndexes = Reserved.getIndexes(this);
        this.builders = new ArrayList<>();
        this.properties = new LinkedHashMap<>();
        this.constants = new LinkedHashMap<>();
    }

    public ProtobufObjectType type() {
        return type;
    }

    public TypeElement element() {
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

    public Optional<String> addConstant(int fieldIndex, String fieldName) {
        return Optional.ofNullable(constants.put(fieldIndex, fieldName));
    }

    public Optional<ProtobufPropertyElement> addProperty(Element element, Element accessor, ProtobufPropertyType type, ProtobufProperty property) {
        var fieldName = element.getSimpleName().toString();
        var result = new ProtobufPropertyElement(fieldName, accessor, type, property, element instanceof ExecutableElement);
        return Optional.ofNullable(properties.put(property.index(), result));
    }

    public Optional<ProtobufPropertyElement> addProperty(ProtobufPropertyType type, ProtobufSerializer.GroupProperty property) {
        var result = new ProtobufPropertyElement(type, property);
        return Optional.ofNullable(properties.put(property.index(), result));
    }

    public void addBuilder(String className, List<? extends VariableElement> parameters, ExecutableElement executableElement) {
        var builderElement = new ProtobufBuilderElement(className, parameters, executableElement);
        builders.add(builderElement);
    }

    public List<ProtobufBuilderElement> builders() {
        return Collections.unmodifiableList(builders);
    }

    public Optional<ProtobufConverterMethod> serializer() {
        return Optional.ofNullable(serializer);
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

    public Set<String> reservedNames() {
        return reservedNames;
    }

    public boolean isNameDisallowed(String name) {
        return reservedNames.contains(name);
    }

    public Set<? extends ProtobufReservedIndex> reservedIndexes() {
        return reservedIndexes;
    }

    public boolean isIndexDisallowed(int index) {
        return !reservedIndexes.stream()
                .allMatch(entry -> entry.allows(index));
    }
}

package it.auties.protobuf.serialization.model;

import it.auties.protobuf.annotation.ProtobufProperty;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.*;

public class ProtobufMessageElement {
    private final TypeElement typeElement;
    private final Map<Integer, ProtobufPropertyStub> properties;
    private final Map<Integer, String> constants;
    private final ProtobufEnumMetadata enumMetadata;

    public ProtobufMessageElement(TypeElement typeElement, ProtobufEnumMetadata enumMetadata) {
        this.typeElement = typeElement;
        this.enumMetadata = enumMetadata;
        this.properties = new LinkedHashMap<>();
        this.constants = new LinkedHashMap<>();
    }

    public TypeElement element() {
        return typeElement;
    }

    public String generatedClassName() {
        var name = new StringBuilder();
        var element = element();
        while (element.getEnclosingElement() instanceof TypeElement parent) {
            name.append(parent.getSimpleName());
            element = parent;
        }

        name.append(element().getSimpleName());
        return name + "Spec";
    }

    public Optional<ProtobufEnumMetadata> enumMetadata() {
        return Optional.of(enumMetadata);
    }

    public List<ProtobufPropertyStub> properties() {
        return List.copyOf(properties.values());
    }

    public boolean isEnum() {
        return typeElement.getKind() == ElementKind.ENUM;
    }

    public Map<Integer, String> constants() {
        return Collections.unmodifiableMap(constants);
    }

    public Optional<String> addConstant(int fieldIndex, String fieldName) {
        return Optional.ofNullable(constants.put(fieldIndex, fieldName));
    }

    public Optional<ProtobufPropertyStub> addProperty(VariableElement element, ProtobufPropertyType type, ProtobufProperty property) {
        if(property.ignored()) {
            return Optional.empty();
        }

        var fieldName = element.getSimpleName().toString();
        var fieldIndex = property.index();
        var result = new ProtobufPropertyStub(fieldIndex, fieldName, type, property);
        return Optional.ofNullable(properties.put(fieldIndex, result));
    }
}

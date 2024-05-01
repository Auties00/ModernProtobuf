package it.auties.protobuf.serialization.object;

import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.serialization.property.ProtobufPropertyElement;
import it.auties.protobuf.serialization.property.ProtobufPropertyType;

import javax.lang.model.element.*;
import java.util.*;

public class ProtobufMessageElement {
    private final TypeElement typeElement;
    private final Map<Integer, ProtobufPropertyElement> properties;
    private final List<ProtobufBuilderElement> builders;
    private final Map<Integer, String> constants;
    private final ProtobufEnumMetadata enumMetadata;

    public ProtobufMessageElement(TypeElement typeElement, ProtobufEnumMetadata enumMetadata) {
        this.typeElement = typeElement;
        this.enumMetadata = enumMetadata;
        this.builders = new ArrayList<>();
        this.properties = new LinkedHashMap<>();
        this.constants = new LinkedHashMap<>();
    }

    public TypeElement element() {
        return typeElement;
    }

    public String getGeneratedClassNameBySuffix(String suffix) {
       return getGeneratedClassNameByName(element().getSimpleName() + suffix);
    }

    public String getGeneratedClassNameByName(String className) {
        var name = new StringBuilder();
        var element = element();
        while (element.getEnclosingElement() instanceof TypeElement parent) {
            name.append(parent.getSimpleName());
            element = parent;
        }

        return name + className;
    }

    public Optional<ProtobufEnumMetadata> enumMetadata() {
        return Optional.of(enumMetadata);
    }

    public List<ProtobufPropertyElement> properties() {
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

    public Optional<ProtobufPropertyElement> addProperty(Element element, Element accessor, ProtobufPropertyType type, ProtobufProperty property) {
        if(property.ignored()) {
            return Optional.empty();
        }

        var fieldName = element.getSimpleName().toString();
        var result = new ProtobufPropertyElement(fieldName, accessor, type, property);
        return Optional.ofNullable(properties.put(property.index(), result));
    }

    public void addBuilder(String className, List<? extends VariableElement> parameters, ExecutableElement executableElement) {
        builders.add(new ProtobufBuilderElement(className, parameters, executableElement));
    }

    public List<ProtobufBuilderElement> builders() {
        return Collections.unmodifiableList(builders);
    }
}

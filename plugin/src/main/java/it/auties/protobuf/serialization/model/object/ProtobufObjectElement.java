package it.auties.protobuf.serialization.model.object;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.serialization.model.property.ProtobufPropertyElement;
import it.auties.protobuf.serialization.model.property.ProtobufPropertyType;

import javax.lang.model.element.*;
import java.util.*;

public class ProtobufObjectElement {
    private final TypeElement typeElement;
    private final Map<Integer, ProtobufPropertyElement> properties;
    private final List<ProtobufBuilderElement> builders;
    private final Map<Integer, String> constants;
    private final ProtobufEnumMetadata enumMetadata;
    private final ExecutableElement deserializer;
    private final Set<String> reservedNames;
    private final Set<? extends ReservedIndex> reservedIndexes;
    private ProtobufUnknownFieldsElement unknownFieldsElement;
    private final boolean group;

    public ProtobufObjectElement(TypeElement typeElement, ProtobufEnumMetadata enumMetadata, ExecutableElement deserializer, boolean group) {
        this.typeElement = typeElement;
        this.enumMetadata = enumMetadata;
        this.deserializer = deserializer;
        this.reservedNames = getReservedNames();
        this.reservedIndexes = getReservedIndexes();
        this.builders = new ArrayList<>();
        this.properties = new LinkedHashMap<>();
        this.constants = new LinkedHashMap<>();
        this.group = group;
    }

    private Set<String> getReservedNames() {
        if(enumMetadata != null) {
            var enumeration = typeElement.getAnnotation(ProtobufEnum.class);
            return enumeration == null ? Set.of() : Set.of(enumeration.reservedNames());
        }

        if(group) {
            var group = typeElement.getAnnotation(ProtobufGroup.class);
            return group == null ? Set.of() : Set.of(group.reservedNames());
        }

        var message = typeElement.getAnnotation(ProtobufMessage.class);
        return message == null ? Set.of() : Set.of(message.reservedNames());
    }

    private Set<ReservedIndex> getReservedIndexes() {
        if(enumMetadata != null) {
            var enumeration = typeElement.getAnnotation(ProtobufEnum.class);
            if (enumeration == null) {
                return Set.of();
            }

            return getReservedIndexes(enumeration.reservedIndexes(), enumeration.reservedRanges());
        }

        if(group) {
            var group = typeElement.getAnnotation(ProtobufGroup.class);
            if (group == null) {
                return Set.of();
            }

            return getReservedIndexes(group.reservedIndexes(), group.reservedRanges());
        }

        var message = typeElement.getAnnotation(ProtobufMessage.class);
        if (message == null) {
            return Set.of();
        }

        return getReservedIndexes(message.reservedIndexes(), message.reservedRanges());
    }

    private Set<ReservedIndex> getReservedIndexes(int[] indexes, ProtobufReservedRange[] ranges) {
        var results = new HashSet<ReservedIndex>();
        for(var index : indexes) {
            results.add(new ReservedIndex.Value(index));
        }

        for(var range : ranges) {
            results.add(new ReservedIndex.Range(range.min(), range.max()));
        }

        return results;
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
        var fieldName = element.getSimpleName().toString();
        var result = new ProtobufPropertyElement(fieldName, accessor, type, property, element instanceof ExecutableElement);
        return Optional.ofNullable(properties.put(property.index(), result));
    }

    public void addBuilder(String className, List<? extends VariableElement> parameters, ExecutableElement executableElement) {
        var builderElement = new ProtobufBuilderElement(className, parameters, executableElement);
        builders.add(builderElement);
    }

    public List<ProtobufBuilderElement> builders() {
        return Collections.unmodifiableList(builders);
    }

    public Optional<ExecutableElement> deserializer() {
        return Optional.ofNullable(deserializer);
    }

    public Optional<ProtobufUnknownFieldsElement> unknownFieldsElement() {
        return Optional.ofNullable(unknownFieldsElement);
    }

    public void setUnknownFieldsElement(ProtobufUnknownFieldsElement unknownFieldsElement) {
        this.unknownFieldsElement = unknownFieldsElement;
    }

    public boolean isGroup() {
        return group;
    }

    public Set<String> reservedNames() {
        return reservedNames;
    }

    public boolean isNameDisallowed(String name) {
        return reservedNames.contains(name);
    }

    public Set<? extends ReservedIndex> reservedIndexes() {
        return reservedIndexes;
    }

    public boolean isIndexDisallowed(int index) {
        return !reservedIndexes.stream()
                .allMatch(entry -> entry.isAllowed(index));
    }


    public sealed interface ReservedIndex {
        static ReservedIndex range(int min, int max) {
            return new Range(min, max);
        }

        static ReservedIndex value(int index) {
            return new Value(index);
        }

        boolean isAllowed(int index);

        record Range(int min, int max) implements ReservedIndex {
            @Override
            public boolean isAllowed(int index) {
                return index < min || index > max;
            }
        }

        record Value(int value) implements ReservedIndex {
            @Override
            public boolean isAllowed(int index) {
                return index != value;
            }
        }
    }
}

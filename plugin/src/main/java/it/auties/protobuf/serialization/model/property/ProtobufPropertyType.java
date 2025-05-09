package it.auties.protobuf.serialization.model.property;

import it.auties.protobuf.annotation.ProtobufGroup;
import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.serialization.model.converter.attributed.ProtobufAttributedConverterDeserializer;
import it.auties.protobuf.serialization.model.converter.ProtobufConverterElement;
import it.auties.protobuf.serialization.model.converter.attributed.ProtobufAttributedConverterSerializer;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.*;

// A representation of a protobuf type
public sealed interface ProtobufPropertyType {
    // The protobuf type of the field
    // For example: required string field = 1;
    // The protobuf type here is string
    ProtobufType protobufType();

    // List of converter used as middlewares between:
    // 1. The protobuf input -> the model
    // 2. The protobuf model -> the output
    List<ProtobufConverterElement> converters();

    // Adds a nullable converter to the type
    void addConverter(ProtobufConverterElement element);

    // Remove all converters
    void clearConverters();

    // The type of the Element that describes this property
    // This can be interpreted as the input type as this is used by the deserializer and builder
    //
    // This is also the parameter's type in the constructor of the enclosing ProtobufMessage
    // This is guaranteed by the ProtobufJavacPlugin#hasPropertiesConstructor check
    //
    // The associated descriptor can either be:
    // 1. VariableElement(class field/record component)
    // 2. ExecutableElement (@ProtobufGetter with no VariableElement associated by index)
    TypeMirror descriptorElementType();

    // The type returned by the accessor for the property
    // This can be interpreted as the output type as this is used by the serializer
    // Hierarchy for accessor resolution(from most important to least important):
    // 1. @ProtobufGetter with the same index
    // 2. Field (accessible if public, protected or package private)
    // 3. Getter/Accessor method
    TypeMirror accessorType();

    // The default valueType of the type
    // For a primitive type, the valueType is 0 (or false)
    // For an object, it's null, or the default valueType assigned by @ProtobufDefaultValue in a ProtobufMixin registered in a @ProtobufProperty
    String descriptorDefaultValue();

    // The mixins associated to this type
    List<TypeElement> mixins();

    // Default implementation to get the serializers for the converters
    default List<ProtobufAttributedConverterSerializer> serializers() {
        return converters()
                .stream()
                .filter(entry -> entry instanceof ProtobufAttributedConverterSerializer)
                .map(entry -> (ProtobufAttributedConverterSerializer) entry)
                .toList();
    }

    // Default implementation to get the deserializers for the converters
    default List<ProtobufAttributedConverterDeserializer> deserializers() {
        return converters()
                .stream()
                .filter(entry -> entry instanceof ProtobufAttributedConverterDeserializer)
                .map(entry -> (ProtobufAttributedConverterDeserializer) entry)
                .toList();
    }

    default TypeMirror serializedType() {
        var serializers = serializers();
        if (serializers.isEmpty()) {
            return descriptorElementType();
        }

        return serializers.getLast()
                .returnType();
    }

    default TypeMirror deserializedType() {
        var deserializers = deserializers();
        if (deserializers.isEmpty()) {
            return descriptorElementType();
        }

        return deserializers.getLast()
                .returnType();
    }

    default Optional<ProtobufAttributedConverterSerializer> rawGroupSerializer() {
        var concreteGroup = false;
        var serializers = serializers();
        for (var serializer : serializers) {
            concreteGroup = !(serializer.parameterType() instanceof DeclaredType declaredType)
                    || !(declaredType.asElement() instanceof TypeElement typeElement)
                    || typeElement.getAnnotation(ProtobufGroup.class) != null;
            if (concreteGroup) {
                break;
            }
        }

        if (concreteGroup || serializers.size() < 2) {
            return Optional.empty();
        }

        return Optional.ofNullable(serializers.get(serializers.size() - 2));
    }

    final class NormalType implements ProtobufPropertyType {
        private final ProtobufType protobufType;
        private final TypeMirror descriptorElementType;
        private final TypeMirror accessorType;
        private final List<ProtobufConverterElement> converters;
        private final String descriptorDefaultValue;
        private final List<TypeElement> mixins;
        private String deserializedDefaultValue;

        public NormalType(ProtobufType protobufType, TypeMirror descriptorElementType, TypeMirror accessorType, String descriptorDefaultValue, List<TypeElement> mixins) {
            this.protobufType = protobufType;
            this.descriptorElementType = descriptorElementType;
            this.accessorType = accessorType;
            this.converters = new ArrayList<>();
            this.descriptorDefaultValue = descriptorDefaultValue;
            this.mixins = mixins;
        }

        @Override
        public List<ProtobufConverterElement> converters() {
            return Collections.unmodifiableList(converters);
        }

        @Override
        public void addConverter(ProtobufConverterElement element) {
            converters.add(element);
        }

        @Override
        public void clearConverters() {
            converters.clear();
        }

        @Override
        public ProtobufType protobufType() {
            return protobufType;
        }

        @Override
        public TypeMirror descriptorElementType() {
            return descriptorElementType;
        }

        @Override
        public TypeMirror accessorType() {
            return accessorType;
        }

        @Override
        public String descriptorDefaultValue() {
            return descriptorDefaultValue;
        }

        public Optional<String> deserializedDefaultValue() {
            return Optional.ofNullable(deserializedDefaultValue);
        }

        public void setDeserializedDefaultValue(String deserializedDefaultValue) {
            this.deserializedDefaultValue = deserializedDefaultValue;
        }

        @Override
        public List<TypeElement> mixins() {
            return Collections.unmodifiableList(mixins);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (NormalType) obj;
            return Objects.equals(this.protobufType, that.protobufType) &&
                    Objects.equals(this.descriptorElementType, that.descriptorElementType) &&
                    Objects.equals(this.accessorType, that.accessorType) &&
                    Objects.equals(this.converters, that.converters) &&
                    Objects.equals(this.descriptorDefaultValue, that.descriptorDefaultValue);
        }

        @Override
        public int hashCode() {
            return Objects.hash(protobufType, descriptorElementType, accessorType, converters, descriptorDefaultValue);
        }

        @Override
        public String toString() {
            return "NormalType[" +
                    "protobufType=" + protobufType + ", " +
                    "descriptorElementType=" + descriptorElementType + ", " +
                    "accessorType=" + accessorType + ", " +
                    "defaultValue=" + descriptorDefaultValue + ']';
        }
    }

    final class MapType implements ProtobufPropertyType {
        private final TypeMirror descriptorElementType;
        private final NormalType keyType;
        private final NormalType valueType;
        private final String descriptorDefaultValue;
        private final List<TypeElement> mixins;
        private final List<ProtobufConverterElement> converters;

        public MapType(TypeMirror descriptorElementType, NormalType keyType, NormalType valueType, String descriptorDefaultValue, List<TypeElement> mixins) {
            this.descriptorElementType = descriptorElementType;
            this.keyType = keyType;
            this.valueType = valueType;
            this.descriptorDefaultValue = descriptorDefaultValue;
            this.mixins = mixins;
            this.converters = new ArrayList<>();
        }

        @Override
        public TypeMirror accessorType() {
            return descriptorElementType;
        }

        @Override
        public List<ProtobufConverterElement> converters() {
            return Collections.unmodifiableList(converters);
        }

        @Override
        public List<TypeElement> mixins() {
            return Collections.unmodifiableList(mixins);
        }

        @Override
        public void addConverter(ProtobufConverterElement element) {
            converters.add(element);
        }

        @Override
        public void clearConverters() {
            converters.clear();
        }

        @Override
        public ProtobufType protobufType() {
            return ProtobufType.MAP;
        }

        @Override
        public TypeMirror descriptorElementType() {
            return descriptorElementType;
        }

        public NormalType keyType() {
            return keyType;
        }

        public NormalType valueType() {
            return valueType;
        }

        @Override
        public String descriptorDefaultValue() {
            return descriptorDefaultValue;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (MapType) obj;
            return Objects.equals(this.descriptorElementType, that.descriptorElementType) &&
                    Objects.equals(this.keyType, that.keyType) &&
                    Objects.equals(this.valueType, that.valueType) &&
                    Objects.equals(this.descriptorDefaultValue, that.descriptorDefaultValue) &&
                    Objects.equals(this.mixins, that.mixins) &&
                    Objects.equals(this.converters, that.converters);
        }

        @Override
        public int hashCode() {
            return Objects.hash(descriptorElementType, keyType, valueType, descriptorDefaultValue, mixins, converters);
        }

        @Override
        public String toString() {
            return "MapType[" +
                    "descriptorElementType=" + descriptorElementType + ", " +
                    "keyType=" + keyType + ", " +
                    "valueType=" + valueType + ", " +
                    "descriptorDefaultValue=" + descriptorDefaultValue + ", " +
                    "mixins=" + mixins + ", " +
                    "converters=" + converters + ']';
        }
    }

    record CollectionType(TypeMirror descriptorElementType, NormalType valueType, String descriptorDefaultValue,
                          List<TypeElement> mixins) implements ProtobufPropertyType {
        @Override
        public TypeMirror accessorType() {
            return descriptorElementType;
        }

        @Override
        public ProtobufType protobufType() {
            return valueType.protobufType();
        }

        // The fact that a type is a CollectionType is inferred from the fact that it's assignable to Collection
        // So by hypothesis it cannot have any converters
        @Override
        public List<ProtobufConverterElement> converters() {
            return List.of();
        }

        @Override
        public List<TypeElement> mixins() {
            return Collections.unmodifiableList(mixins);
        }

        @Override
        public void addConverter(ProtobufConverterElement element) {

        }

        @Override
        public void clearConverters() {

        }
    }
}

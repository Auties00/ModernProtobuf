package it.auties.protobuf.serialization.property;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.serialization.converter.ProtobufConverterElement;
import it.auties.protobuf.serialization.converter.ProtobufDeserializerElement;
import it.auties.protobuf.serialization.converter.ProtobufSerializerElement;

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

    // The default value of the type
    // For a primitive type, the value is 0 (or false)
    // For an object, it's null, or the default value assigned by @ProtobufDefaultValue in a ProtobufMixin registered in a @ProtobufProperty
    String defaultValue();

    // Adds a nullable converter to the type
    void addNullableConverter(ProtobufConverterElement element);

    // Default implementation to get the serializers for the converters
    default List<ProtobufSerializerElement> serializers() {
        return converters()
                .stream()
                .filter(entry -> entry instanceof ProtobufSerializerElement)
                .map(entry -> (ProtobufSerializerElement) entry)
                .toList();
    }

    // Default implementation to get the deserializers for the converters
    default List<ProtobufDeserializerElement> deserializers() {
        return converters()
                .stream()
                .filter(entry -> entry instanceof ProtobufDeserializerElement)
                .map(entry -> (ProtobufDeserializerElement) entry)
                .toList();
    }

    default TypeMirror serializedType() {
        var serializers = serializers();
        if(serializers.isEmpty()) {
            return descriptorElementType();
        }

        return serializers.getLast().returnType();
    }

    default TypeMirror deserializedType() {
        var deserializers = deserializers();
        if(deserializers.isEmpty()) {
            return descriptorElementType();
        }

        return deserializers.getLast().parameterType();
    }

    final class NormalType implements ProtobufPropertyType {
        private final ProtobufType protobufType;
        private final TypeMirror descriptorElementType;
        private final TypeMirror accessorType;
        private final List<ProtobufConverterElement> converters;
        private final String defaultValue;
        private String deserializedDefaultValue;

        public NormalType(ProtobufType protobufType, TypeMirror descriptorElementType, TypeMirror accessorType, String defaultValue) {
            this.protobufType = protobufType;
            this.descriptorElementType = descriptorElementType;
            this.accessorType = accessorType;
            this.converters = new ArrayList<>();
            this.defaultValue = defaultValue;
        }

        @Override
        public List<ProtobufConverterElement> converters() {
            return Collections.unmodifiableList(converters);
        }

        @Override
        public void addNullableConverter(ProtobufConverterElement element) {
            if (element == null) {
                return;
            }

            converters.add(element);
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
        public String defaultValue() {
            return Objects.requireNonNullElse(defaultValue, "The default value was not computed");
        }

        public Optional<String> deserializedDefaultValue() {
            return Optional.ofNullable(deserializedDefaultValue);
        }

        public void setDeserializedDefaultValue(String deserializedDefaultValue) {
            this.deserializedDefaultValue = deserializedDefaultValue;
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
                    Objects.equals(this.defaultValue, that.defaultValue);
        }

        @Override
        public int hashCode() {
            return Objects.hash(protobufType, descriptorElementType, accessorType, converters, defaultValue);
        }

        @Override
        public String toString() {
            return "NormalType[" +
                    "protobufType=" + protobufType + ", " +
                    "descriptorElementType=" + descriptorElementType + ", " +
                    "accessorType=" + accessorType + ", " +
                    "converters=" + converters + ", " +
                    "defaultValue=" + defaultValue + ']';
        }
    }

    record CollectionType(TypeMirror descriptorElementType, NormalType value, String defaultValue) implements ProtobufPropertyType {
        @Override
        public TypeMirror accessorType() {
            return descriptorElementType;
        }

        @Override
        public ProtobufType protobufType() {
            return value.protobufType();
        }

        @Override
        public List<ProtobufConverterElement> converters() {
            return value.converters();
        }

        @Override
        public void addNullableConverter(ProtobufConverterElement element) {
            if(element == null) {
                return;
            }

            value.addNullableConverter(element);
        }
    }

    record MapType(TypeMirror descriptorElementType, NormalType keyType, NormalType valueType, String defaultValue) implements ProtobufPropertyType {
        @Override
        public TypeMirror accessorType() {
            return descriptorElementType;
        }

        @Override
        public List<ProtobufConverterElement> converters() {
            return Collections.emptyList();
        }

        @Override
        public void addNullableConverter(ProtobufConverterElement element) {
            if(element == null) {
                return;
            }

            keyType.addNullableConverter(element);
            valueType.addNullableConverter(element);
        }

        @Override
        public ProtobufType protobufType() {
            return ProtobufType.MAP;
        }
    }
}

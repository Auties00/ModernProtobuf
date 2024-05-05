package it.auties.protobuf.serialization.property;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.serialization.converter.ProtobufConverterElement;
import it.auties.protobuf.serialization.converter.ProtobufDeserializerElement;
import it.auties.protobuf.serialization.converter.ProtobufSerializerElement;

import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    // Hierarchy of importance for type inference:
    // 1. Accessor/getter type
    // 2. Field type
    // !! Override type is ignored
    // Here are a couple of examples:
    // - Type described by a field
    //    Here the abstract type is int
    //    class Message implements ProtobufMessage {
    //         @ProtobufProperty(index = 1, type = UINT32)
    //         int message; // Public or package private field with no accessor/getter
    //         public Message(int message) {
    //             this.message = message;
    //         }
    //    }
    // -  Type described by a field with overrideType specified
    //    Here the abstract type is int (not Integer)
    //    class Message implements ProtobufMessage {
    //         @ProtobufProperty(index = 1, type = UINT32, overrideType = Integer.class)
    //         int message; // Public or package private field with no accessor/getter
    //         public Message(int message) {
    //             this.message = message;
    //         }
    //    }
    // -  Type described by a getter/accessor
    //    Here the abstract type is Integer (not int)
    //    class Message implements ProtobufMessage {
    //         @ProtobufProperty(index = 1, type = UINT32)
    //         private int message; // Type modifier is irrelevant, accessor/getter is considered more important
    //         public Message(int message) {
    //             this.message = message;
    //         }
    //
    //         public Integer message() { // Public or package private
    //             return message;
    //         }
    //    }
    // -  Type described by a getter/accessor with overrideType specified
    //    Here the abstract type is Integer (not Long)
    //    class Message implements ProtobufMessage {
    //         @ProtobufProperty(index = 1, type = UINT32, overrideType = Long.class)
    //         private int message; // Type modifier is irrelevant, accessor/getter is considered more important
    //         public Message(int message) {
    //             this.message = message;
    //         }
    //
    //         public Integer message() { // Public or package private
    //             return message;
    //         }
    //    }
    TypeMirror descriptorElementType();

    // The actual implementation used for this property
    // Hierarchy of importance for type inference:
    // 1. Override type
    // 2. Accessor/getter type
    // 3. Field type
    // Here are a couple of examples:
    // -  Type described by a field
    //    Here the implementation type is int
    //    class Message implements ProtobufMessage {
    //         @ProtobufProperty(index = 1, type = UINT32)
    //         int message; // Public or package private field with no accessor/getter
    //         public Message(int message) {
    //             this.message = message;
    //         }
    //    }
    // -  Type described by a field with overrideType specified
    //    Here the implementation type is Integer (not int)
    //    class Message implements ProtobufMessage {
    //         @ProtobufProperty(index = 1, type = UINT32, overrideType = Integer.class)
    //         int message; // Public or package private field with no accessor/getter
    //         public Message(int message) {
    //             this.message = message;
    //         }
    //    }
    // -  Type described by a getter/accessor
    //    Here the implementation type is Integer (not int)
    //    class Message implements ProtobufMessage {
    //         @ProtobufProperty(index = 1, type = UINT32)
    //         private int message; // Type modifier is irrelevant, accessor/getter is considered more important
    //         public Message(int message) {
    //             this.message = message;
    //         }
    //
    //         public Integer message() { // Public or package private
    //             return message;
    //         }
    //    }
    // -  Type described by a getter/accessor with overrideType specified
    //    Here the implementation type is Long (not Long)
    //    class Message implements ProtobufMessage {
    //         @ProtobufProperty(index = 1, type = UINT32, overrideType = Long.class)
    //         private int message; // Type modifier is irrelevant, accessor/getter is considered more important
    //         public Message(int message) {
    //             this.message = message;
    //         }
    //
    //         public Integer message() { // Public or package private
    //             return message;
    //         }
    //    }
    TypeMirror implementationType();

    // The default value of the type
    // For a primitive type, the value is 0 (or false)
    // For an object, it's null, or the default value assigned by @ProtobufDefaultValue in a ProtobufMixin registered in a @ProtobufProperty
    String defaultValue();

    // Adds a nullable converter to the type
    void addNullableConverter(ProtobufConverterElement element);

    // Returns whether the type is a primitive
    // The concreteType should be checked, not the descriptor type
    boolean isPrimitive();

    // Returns whether the type is a message
    // The concreteType should be checked, not the descriptor type
    boolean isMessage();

    // Returns whether the type is an enum
    // The concreteType should be checked, not the descriptor type
    boolean isEnum();

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

    record NormalType(ProtobufType protobufType, TypeMirror descriptorElementType, TypeMirror implementationType, List<ProtobufConverterElement> converters, String defaultValue, boolean isMessage, boolean isEnum) implements ProtobufPropertyType {
        public NormalType(ProtobufType protobufType, TypeMirror fieldType, TypeMirror implementationType, String defaultValue, boolean isMessage, boolean isEnum) {
            this(protobufType, fieldType, implementationType, new ArrayList<>(), defaultValue, isMessage, isEnum);
        }

        @Override
        public boolean isPrimitive() {
            return implementationType.getKind().isPrimitive();
        }

        @Override
        public List<ProtobufConverterElement> converters() {
            return Collections.unmodifiableList(converters);
        }

        @Override
        public void addNullableConverter(ProtobufConverterElement element) {
            if(element == null) {
                return;
            }

            converters.add(element);
        }
    }

    record CollectionType(TypeMirror descriptorElementType, NormalType value, String defaultValue) implements ProtobufPropertyType {
        @Override
        public TypeMirror implementationType() {
            return descriptorElementType;
        }

        @Override
        public ProtobufType protobufType() {
            return value.protobufType();
        }

        @Override
        public boolean isMessage() {
            return value.isMessage();
        }

        @Override
        public boolean isEnum() {
            return value.isEnum();
        }

        @Override
        public boolean isPrimitive() {
            return value.isPrimitive();
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
        public boolean isPrimitive() {
           return false;
        }

        @Override
        public boolean isEnum() {
            return false;
        }

        @Override
        public boolean isMessage() {
            return false;
        }

        @Override
        public TypeMirror implementationType() {
            return descriptorElementType;
        }

        @Override
        public List<ProtobufConverterElement> converters() {
            var results = new ArrayList<ProtobufConverterElement>();
            results.addAll(keyType.converters());
            results.addAll(valueType.converters());
            return Collections.unmodifiableList(results);
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

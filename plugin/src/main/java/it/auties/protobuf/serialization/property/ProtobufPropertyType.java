package it.auties.protobuf.serialization.property;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.serialization.converter.ProtobufConverterElement;
import it.auties.protobuf.serialization.converter.ProtobufDeserializerElement;
import it.auties.protobuf.serialization.converter.ProtobufSerializerElement;

import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public sealed interface ProtobufPropertyType {
    ProtobufType protobufType();
    List<ProtobufConverterElement> converters();
    TypeMirror fieldType();
    TypeMirror implementationType();
    void addNullableConverter(ProtobufConverterElement element);
    boolean isPrimitive();
    boolean isEnum();

    default List<ProtobufSerializerElement> serializers() {
        return converters().stream()
                .filter(entry -> entry instanceof ProtobufSerializerElement)
                .map(entry -> (ProtobufSerializerElement) entry)
                .toList();
    }

    default List<ProtobufDeserializerElement> deserializers() {
        return converters().stream()
                .filter(entry -> entry instanceof ProtobufDeserializerElement)
                .map(entry -> (ProtobufDeserializerElement) entry)
                .toList();
    }

    record NormalType(ProtobufType protobufType, TypeMirror fieldType, TypeMirror implementationType, List<ProtobufConverterElement> converters, boolean isEnum) implements ProtobufPropertyType {
        public NormalType(ProtobufType protobufType, TypeMirror fieldType, TypeMirror implementationType, boolean isEnum) {
            this(protobufType, fieldType, implementationType, new ArrayList<>(), isEnum);
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

    record OptionalType(TypeMirror optionalType, NormalType value) implements ProtobufPropertyType {
        @Override
        public TypeMirror fieldType() {
            return optionalType;
        }

        @Override
        public TypeMirror implementationType() {
            return value.implementationType();
        }

        @Override
        public ProtobufType protobufType() {
            return value.protobufType();
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
            value.addNullableConverter(element);
        }
    }

    record AtomicType(TypeMirror atomicType, NormalType value) implements ProtobufPropertyType {
        @Override
        public TypeMirror fieldType() {
            return atomicType;
        }

        @Override
        public TypeMirror implementationType() {
            return value.implementationType();
        }

        @Override
        public ProtobufType protobufType() {
            return value.protobufType();
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
            value.addNullableConverter(element);
        }
    }

    record CollectionType(TypeMirror fieldType, TypeMirror collectionType, NormalType value) implements ProtobufPropertyType {
        @Override
        public TypeMirror fieldType() {
            return fieldType;
        }

        @Override
        public TypeMirror implementationType() {
            return value.implementationType();
        }

        @Override
        public ProtobufType protobufType() {
            return value.protobufType();
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
            value.addNullableConverter(element);
        }
    }

    record MapType(TypeMirror fieldType, TypeMirror mapType, NormalType keyType, NormalType valueType) implements ProtobufPropertyType {
        public boolean isPrimitive() {
           return false;
        }

        @Override
        public boolean isEnum() {
            return false;
        }

        @Override
        public TypeMirror implementationType() {
            return fieldType;
        }

        @Override
        public List<ProtobufConverterElement> converters() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addNullableConverter(ProtobufConverterElement element) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProtobufType protobufType() {
            return ProtobufType.MAP;
        }
    }
}

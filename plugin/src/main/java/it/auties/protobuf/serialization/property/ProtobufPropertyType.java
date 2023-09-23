package it.auties.protobuf.serialization.property;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.serialization.converter.ProtobufConverterElement;
import it.auties.protobuf.serialization.converter.ProtobufDeserializerElement;
import it.auties.protobuf.serialization.converter.ProtobufSerializerElement;

import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;

public final class ProtobufPropertyType {
    private final ProtobufType protobufType;
    private final TypeMirror fieldType;
    private final TypeMirror implementation;
    private final TypeMirror concreteCollection;
    private final List<ProtobufSerializerElement> serializers;
    private final List<ProtobufDeserializerElement> deserializers;
    private final boolean isOptional;
    private final boolean isEnum;

    public ProtobufPropertyType(ProtobufType protobufType, TypeMirror fieldType, TypeMirror implementationType, TypeMirror concreteCollection, boolean isOptional, boolean isEnum) {
        this.protobufType = protobufType;
        this.fieldType = fieldType;
        this.implementation = implementationType;
        this.concreteCollection = concreteCollection;
        this.serializers = new ArrayList<>();
        this.deserializers = new ArrayList<>();
        this.isOptional = isOptional;
        this.isEnum = isEnum;
    }

    public ProtobufType protobufType() {
        return protobufType;
    }

    public TypeMirror fieldType() {
        return fieldType;
    }

    public TypeMirror implementationType() {
        return implementation;
    }

    public TypeMirror concreteCollectionType() {
        return concreteCollection;
    }

    public void addNullableConverter(ProtobufConverterElement converter) {
        if (converter == null) {
            return;
        }

        switch (converter.type()) {
            case SERIALIZER -> serializers.add((ProtobufSerializerElement) converter);
            case DESERIALIZER -> deserializers.add((ProtobufDeserializerElement) converter);
        }
    }

    public List<ProtobufSerializerElement> serializers() {
        return serializers;
    }

    public List<ProtobufDeserializerElement> deserializers() {
        return deserializers;
    }

    public boolean isEnum() {
        return isEnum;
    }

    public boolean isOptional() {
        return isOptional;
    }

    public boolean isPrimitive() {
        return fieldType.getKind().isPrimitive();
    }
}

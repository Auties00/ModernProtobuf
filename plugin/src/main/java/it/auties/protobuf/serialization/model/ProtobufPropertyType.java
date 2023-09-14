package it.auties.protobuf.serialization.model;

import it.auties.protobuf.model.ProtobufType;

import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProtobufPropertyType {
    private final ProtobufType protobufType;
    private final TypeMirror fieldType;
    private final TypeMirror implementation;
    private final TypeMirror concreteCollection;
    private final List<ProtobufConverterElement> converters;
    private final boolean isEnum;

    public ProtobufPropertyType(ProtobufType protobufType, TypeMirror fieldType, TypeMirror implementationType, TypeMirror concreteCollection, boolean isEnum) {
        this.protobufType = protobufType;
        this.fieldType = fieldType;
        this.implementation = implementationType;
        this.concreteCollection = concreteCollection;
        this.converters = new ArrayList<>();
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

        converters.add(converter);
    }

    public List<ProtobufConverterElement> converters() {
        return Collections.unmodifiableList(converters);
    }

    public boolean isEnum() {
        return isEnum;
    }
}

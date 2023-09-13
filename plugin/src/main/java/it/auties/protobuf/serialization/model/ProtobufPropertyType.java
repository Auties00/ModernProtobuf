package it.auties.protobuf.serialization.model;

import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProtobufPropertyType {
    private final TypeMirror fieldType;
    private final TypeMirror implementation;
    private final TypeMirror wrapper;
    private final List<ProtobufConverterElement> converters;
    private final boolean isEnum;

    public ProtobufPropertyType(TypeMirror fieldType, TypeMirror implementationType, TypeMirror wrapperType, boolean isEnum) {
        this.fieldType = fieldType;
        this.implementation = implementationType;
        this.wrapper = wrapperType;
        this.converters = new ArrayList<>();
        this.isEnum = isEnum;
    }

    public TypeMirror fieldType() {
        return fieldType;
    }

    public TypeMirror implementationType() {
        return implementation;
    }

    public TypeMirror wrapperType() {
        return wrapper;
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

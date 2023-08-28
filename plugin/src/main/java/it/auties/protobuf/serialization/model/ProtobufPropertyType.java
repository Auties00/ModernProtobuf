package it.auties.protobuf.serialization.model;

import javax.lang.model.type.TypeMirror;
import java.util.Objects;
import java.util.Optional;

public final class ProtobufPropertyType {
    private final TypeMirror property;
    private final TypeMirror implementation;
    private final TypeMirror wrapper;
    private final ProtobufPropertyConverter converter;
    private final boolean isEnum;

    public ProtobufPropertyType(TypeMirror property, TypeMirror implementation, TypeMirror wrapperType, ProtobufPropertyConverter converter, boolean isEnum) {
        this.property = property;
        this.implementation = implementation;
        this.wrapper = wrapperType;
        this.converter = converter;
        this.isEnum = isEnum;
    }

    public TypeMirror fieldType() {
        return Objects.requireNonNullElse(wrapper, implementation);
    }

    public TypeMirror propertyType() {
        return property;
    }

    public TypeMirror implementationType() {
        return implementation;
    }

    public TypeMirror wrapperType() {
        return wrapper;
    }

    public Optional<ProtobufPropertyConverter> converter() {
        return Optional.ofNullable(converter);
    }

    public boolean isEnum() {
        return isEnum;
    }
}

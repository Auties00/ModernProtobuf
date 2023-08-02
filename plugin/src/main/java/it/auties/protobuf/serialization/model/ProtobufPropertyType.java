package it.auties.protobuf.serialization.model;

import org.objectweb.asm.Type;

import java.util.Objects;
import java.util.Optional;

public final class ProtobufPropertyType {
    private final Type propertyType;
    private final Type implementationType;
    private final Type wrapperType;
    private final ProtobufPropertyConverter converter;
    private final boolean isEnum;

    public ProtobufPropertyType(Type propertyType, Type implementationType, Type wrapperType, ProtobufPropertyConverter converter, boolean isEnum) {
        this.propertyType = propertyType;
        this.implementationType = implementationType;
        this.wrapperType = wrapperType;
        this.converter = converter;
        this.isEnum = isEnum;
    }

    public Type fieldType() {
        return Objects.requireNonNullElse(wrapperType, implementationType);
    }

    public Type propertyType() {
        return propertyType;
    }

    public Type implementationType() {
        return implementationType;
    }

    public Type wrapperType() {
        return wrapperType;
    }

    public Optional<ProtobufPropertyConverter> converter() {
        return Optional.ofNullable(converter);
    }

    public boolean isEnum() {
        return isEnum;
    }
}

package it.auties.protobuf.serialization.model;

import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;
import org.objectweb.asm.Type;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public record ProtobufPropertyStub(int index, String name, ProtobufType protoType, AtomicInteger fieldId, Type javaType,
                                   Type wrapperType,
                                   boolean isEnum, boolean required, boolean repeated, boolean packed) {
    public ProtobufPropertyStub(int index, String name, ProtobufPropertyType type, ProtobufProperty annotation) {
        this(
                index,
                name,
                annotation.type(),
                new AtomicInteger(),
                type.argumentType(),
                type.rawType(),
                type.isEnum(),
                annotation.required(),
                annotation.repeated(),
                annotation.packed()
        );
    }

    public Type fieldType() {
        return Objects.requireNonNullElse(wrapperType, javaType);
    }
}

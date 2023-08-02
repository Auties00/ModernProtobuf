package it.auties.protobuf.serialization.model;

import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.concurrent.atomic.AtomicInteger;

public record ProtobufPropertyStub(int index, String name, ProtobufType protoType, AtomicInteger fieldId, ProtobufPropertyType type, boolean required, boolean repeated, boolean packed) {
    public ProtobufPropertyStub(int index, String name, ProtobufPropertyType type, ProtobufProperty annotation) {
        this(
                index,
                name,
                annotation.type(),
                new AtomicInteger(),
                type,
                annotation.required(),
                annotation.repeated(),
                annotation.packed()
        );
    }
}

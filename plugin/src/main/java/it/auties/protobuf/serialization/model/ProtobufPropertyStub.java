package it.auties.protobuf.serialization.model;

import it.auties.protobuf.annotation.ProtobufProperty;

import javax.lang.model.element.ExecutableElement;

public record ProtobufPropertyStub(int index, String name, ExecutableElement accessor, ProtobufPropertyType type, boolean required, boolean repeated, boolean packed) {
    public ProtobufPropertyStub(int index, String name, ExecutableElement accessor, ProtobufPropertyType type, ProtobufProperty annotation) {
        this(
                index,
                name,
                accessor,
                type,
                annotation.required(),
                annotation.repeated(),
                annotation.packed()
        );
    }
}

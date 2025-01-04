package it.auties.protobuf.serialization.model.property;

import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.annotation.ProtobufSerializer;

import javax.lang.model.element.Element;

public record ProtobufPropertyElement(
        int index,
        String name,
        Element accessor,
        ProtobufPropertyType type,
        boolean required,
        boolean packed,
        boolean synthetic
) {
    public ProtobufPropertyElement(String name, Element accessor, ProtobufPropertyType type, ProtobufProperty annotation, boolean synthetic) {
        this(
                annotation.index(),
                name,
                accessor,
                type,
                annotation.required(),
                annotation.packed(),
                synthetic
        );
    }

    public ProtobufPropertyElement(ProtobufPropertyType type, ProtobufSerializer.GroupProperty annotation) {
        this(
                annotation.index(),
                String.valueOf(annotation.index()),
                null,
                type,
                false,
                annotation.packed(),
                true
        );
    }
}

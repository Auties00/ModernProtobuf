package it.auties.protobuf.serialization.model;

import javax.lang.model.element.Element;

public record ProtobufPropertyElement(
        long index,
        String name,
        Element accessor,
        ProtobufPropertyType type,
        boolean required,
        boolean packed,
        boolean synthetic
) {

}

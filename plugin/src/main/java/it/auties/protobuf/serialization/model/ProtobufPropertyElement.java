package it.auties.protobuf.serialization.model;

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

}

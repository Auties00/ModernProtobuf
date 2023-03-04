package it.auties.protobuf.serialization.performance.model;

import it.auties.protobuf.base.ProtobufType;

import javax.lang.model.element.TypeElement;

public record ProtobufWritable(TypeElement enclosing, TypeElement elementType, String name, int index, ProtobufType type, ProtobufTypeImplementation implementation,
                               boolean required, boolean ignore, boolean packed, boolean repeated) {
}

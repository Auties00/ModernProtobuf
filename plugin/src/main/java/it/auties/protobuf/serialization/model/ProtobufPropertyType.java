package it.auties.protobuf.serialization.model;

import org.objectweb.asm.Type;

public record ProtobufPropertyType(Type argumentType, Type rawType) {
}

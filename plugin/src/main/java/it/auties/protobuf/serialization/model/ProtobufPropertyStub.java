package it.auties.protobuf.serialization.model;

import it.auties.protobuf.model.ProtobufType;
import org.objectweb.asm.Type;

public record ProtobufPropertyStub(int index, String name, ProtobufType protoType, Type javaType, Type wrapperType,
                                   boolean isEnum, boolean required, boolean repeated, boolean packed) {
}

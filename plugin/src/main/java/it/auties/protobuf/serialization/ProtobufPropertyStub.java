package it.auties.protobuf.serialization;

import it.auties.protobuf.model.ProtobufType;
import org.objectweb.asm.Type;

public record ProtobufPropertyStub(int index, String name, ProtobufType protoType, Type javaType, Type wrapperType,
                                   boolean required, boolean ignore, boolean repeated, boolean packed) {
}

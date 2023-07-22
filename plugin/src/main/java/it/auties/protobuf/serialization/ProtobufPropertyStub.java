package it.auties.protobuf.serialization;

import it.auties.protobuf.base.ProtobufType;
import org.objectweb.asm.Type;

public record ProtobufPropertyStub(int index, ProtobufType type, Type implementation, boolean required, boolean ignore, boolean repeated, boolean packed) {
}

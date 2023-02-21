package it.auties.protobuf.serialization.performance.model;

import it.auties.protobuf.base.ProtobufType;

public record ProtobufWritable(String name, int index, ProtobufType type, String implementation, boolean required,
                               boolean ignore, boolean packed, boolean repeated) {

}

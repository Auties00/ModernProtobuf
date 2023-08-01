package it.auties.protobuf.parser.type;

import it.auties.protobuf.model.ProtobufType;

import static it.auties.protobuf.model.ProtobufType.OBJECT;

public sealed interface ProtobufTypeReference permits ProtobufPrimitiveType, ProtobufObjectType {
    ProtobufType protobufType();
    boolean isPrimitive();

    static ProtobufTypeReference of(String type){
        var protobufType = ProtobufType.of(type)
                .orElseThrow(() -> new IllegalArgumentException("Unknown type: " +  type));
        return protobufType == OBJECT ? ProtobufObjectType.unattributed(type) : ProtobufPrimitiveType.of(protobufType);
    }
}

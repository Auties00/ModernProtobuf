package it.auties.protobuf.parser.type;

import it.auties.protobuf.base.ProtobufType;

public sealed interface ProtobufTypeReference permits ProtobufPrimitiveType, ProtobufMessageType {
    String name();
    ProtobufType type();
    boolean primitive();

    static ProtobufTypeReference of(String type){
        var protobufType = ProtobufType.of(type)
                .orElse(ProtobufType.MESSAGE);
        return protobufType == ProtobufType.MESSAGE ? ProtobufMessageType.unattributed(type)
                : ProtobufPrimitiveType.of(protobufType);
    }
}

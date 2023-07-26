package it.auties.protobuf.parser.type;

import it.auties.protobuf.model.ProtobufType;

public sealed interface ProtobufTypeReference permits ProtobufPrimitiveType, ProtobufObjectType {
    ProtobufType protobufType();
    boolean isPrimitive();

    static ProtobufTypeReference of(String type){
        var protobufType = ProtobufType.of(type)
                .orElseThrow(() -> new IllegalArgumentException("Unknown type: " +  type));
        return switch (protobufType) {
            case MESSAGE, ENUM -> ProtobufObjectType.unattributed(type, protobufType == ProtobufType.ENUM);
            default -> ProtobufPrimitiveType.of(protobufType);
        };
    }
}

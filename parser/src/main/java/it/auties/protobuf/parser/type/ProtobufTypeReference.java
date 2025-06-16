package it.auties.protobuf.parser.type;

import it.auties.protobuf.model.ProtobufType;

public sealed interface ProtobufTypeReference
        permits ProtobufGroupType, ProtobufMapType, ProtobufObjectType, ProtobufPrimitiveType {
    String name();
    ProtobufType protobufType();
    boolean isAttributed();

    static ProtobufTypeReference of(String type){
        var protobufType = ProtobufType.of(type);
        return switch (protobufType) {
            case UNKNOWN -> ProtobufObjectType.of(type);
            case MAP -> ProtobufMapType.of();
            case GROUP -> ProtobufGroupType.of(type);
            default -> ProtobufPrimitiveType.of(protobufType);
        };
    }
}

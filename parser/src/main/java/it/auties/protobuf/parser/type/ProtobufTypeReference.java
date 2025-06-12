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
            case UNKNOWN -> ProtobufObjectType.unattributed(type);
            case MAP -> ProtobufMapType.unattributed();
            case GROUP -> ProtobufGroupType.unattributed(type);
            default -> ProtobufPrimitiveType.attributed(protobufType);
        };
    }
}

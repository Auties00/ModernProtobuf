package it.auties.protobuf.parser.type;

import it.auties.protobuf.model.ProtobufType;

import static it.auties.protobuf.model.ProtobufType.OBJECT;

public sealed interface ProtobufTypeReference permits ProtobufPrimitiveType, ProtobufObjectType, ProtobufMapType {
    String name();
    ProtobufType protobufType();
    boolean isAttributed();

    static ProtobufTypeReference of(String type){
        var protobufType = ProtobufType.of(type).orElse(OBJECT);
        return switch (protobufType) {
            case OBJECT ->  ProtobufObjectType.unattributed(type);
            case MAP -> ProtobufMapType.unattributed();
            default -> ProtobufPrimitiveType.attributed(protobufType);
        };
    }
}

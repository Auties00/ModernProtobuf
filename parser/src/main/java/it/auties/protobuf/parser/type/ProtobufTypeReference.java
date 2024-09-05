package it.auties.protobuf.parser.type;

import it.auties.protobuf.model.ProtobufType;

import static it.auties.protobuf.model.ProtobufType.OBJECT;

public sealed interface ProtobufTypeReference permits ProtobufGroupType, ProtobufMapType, ProtobufObjectType, ProtobufPrimitiveType {
    String name();
    ProtobufType protobufType();
    boolean isAttributed();

    @SuppressWarnings("deprecation")
    static ProtobufTypeReference of(String type){
        var protobufType = ProtobufType.of(type)
                .orElse(OBJECT);
        return switch (protobufType) {
            case OBJECT ->  ProtobufObjectType.unattributed(type);
            case MAP -> ProtobufMapType.unattributed();
            case GROUP -> ProtobufGroupType.unattributed(type);
            default -> ProtobufPrimitiveType.attributed(protobufType);
        };
    }
}

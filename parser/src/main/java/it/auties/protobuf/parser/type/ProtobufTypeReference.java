package it.auties.protobuf.parser.type;

import it.auties.protobuf.model.ProtobufType;

public sealed interface ProtobufTypeReference
        permits ProtobufGroupTypeReference, ProtobufMapTypeReference, ProtobufMessageOrEnumTypeReference, ProtobufPrimitiveTypeReference {
    String name();
    ProtobufType protobufType();
    boolean isAttributed();

    static ProtobufTypeReference of(String type){
        var protobufType = ProtobufType.of(type);
        return switch (protobufType) {
            case UNKNOWN -> new ProtobufMessageOrEnumTypeReference(type);
            case MAP -> new ProtobufMapTypeReference();
            case GROUP -> new ProtobufGroupTypeReference(type);
            default -> new ProtobufPrimitiveTypeReference(protobufType);
        };
    }
}

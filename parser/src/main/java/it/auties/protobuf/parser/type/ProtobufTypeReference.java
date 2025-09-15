package it.auties.protobuf.parser.type;

import it.auties.protobuf.model.ProtobufType;

public sealed interface ProtobufTypeReference
        permits ProtobufPrimitiveTypeReference, ProtobufObjectTypeReference {
    String name();
    ProtobufType protobufType();
    boolean isAttributed();
}

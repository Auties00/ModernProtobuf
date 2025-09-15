package it.auties.protobuf.parser.type;

public sealed interface ProtobufObjectTypeReference
        extends ProtobufTypeReference
        permits ProtobufUnresolvedObjectTypeReference, ProtobufMessageTypeReference, ProtobufEnumTypeReference, ProtobufGroupTypeReference, ProtobufMapTypeReference {

}

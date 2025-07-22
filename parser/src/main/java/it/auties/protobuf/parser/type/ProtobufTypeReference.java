package it.auties.protobuf.parser.type;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.parser.tree.*;

public sealed interface ProtobufTypeReference
        permits ProtobufEnumTypeReference, ProtobufGroupTypeReference, ProtobufMapTypeReference, ProtobufUnresolvedTypeReference, ProtobufMessageTypeReference, ProtobufPrimitiveTypeReference {
    String name();
    ProtobufType protobufType();
    boolean isAttributed();

    static ProtobufTypeReference of(String type){
        var protobufType = ProtobufType.of(type);
        return switch (protobufType) {
            case UNKNOWN -> new ProtobufUnresolvedTypeReference(type);
            case MAP -> new ProtobufMapTypeReference();
            case GROUP -> new ProtobufGroupTypeReference(type);
            default -> new ProtobufPrimitiveTypeReference(protobufType);
        };
    }

    static ProtobufTypeReference of(ProtobufTree.WithBodyAndName<?> type) {
        return switch (type) {
            case ProtobufEnumStatement enumeration -> new ProtobufEnumTypeReference(enumeration);
            case ProtobufMessageStatement message -> new ProtobufMessageTypeReference(message);
            case ProtobufOneofFieldStatement ignored -> throw new IllegalArgumentException("Cannot resolve a type reference for a oneof field");
            case ProtobufServiceStatement ignored -> throw new IllegalArgumentException("Cannot resolve a type reference for a service");
        };
    }
}

package it.auties.protobuf.exception;

import lombok.experimental.StandardException;

@StandardException
public class ProtobufDeserializationException extends ProtobufException {
    public static ProtobufDeserializationException truncatedMessage() {
        return new ProtobufDeserializationException("A message ended unexpectedly in the middle of a field");
    }

    public static ProtobufDeserializationException negativeSize() {
        return new ProtobufDeserializationException("A message reported its length as negative");
    }

    public static ProtobufDeserializationException malformedVarInt() {
        return new ProtobufDeserializationException("A message contained a malformed var int");
    }

    public static ProtobufDeserializationException invalidTag(int tag) {
        return new ProtobufDeserializationException("A message contained an invalid tag: %s".formatted(tag));
    }

    public static ProtobufDeserializationException invalidWireType(int tag) {
        return new ProtobufDeserializationException("A message contained an invalid wire type: %s".formatted(tag));
    }
}

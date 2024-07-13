package it.auties.protobuf.exception;

public class ProtobufDeserializationException extends ProtobufException {
    public ProtobufDeserializationException() {
    }

    public ProtobufDeserializationException(String message) {
        super(message);
    }

    public static ProtobufDeserializationException truncatedMessage() {
        return new ProtobufDeserializationException("A message ended unexpectedly in the middle of a field");
    }

    public static ProtobufDeserializationException malformedVarInt() {
        return new ProtobufDeserializationException("A message contained a malformed var int");
    }

    public static ProtobufDeserializationException invalidWireType(int wireType) {
        return new ProtobufDeserializationException("A message contained an invalid wire type: %s".formatted(wireType));
    }
}

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

    public static ProtobufDeserializationException invalidStartObject() {
        return new ProtobufDeserializationException("A message started a new group without closing the previous one");
    }

    public static ProtobufDeserializationException invalidEndObject() {
        return new ProtobufDeserializationException("A message closed a group without opening one");
    }

    public static ProtobufDeserializationException invalidEndObject(int actualFieldIndex, int expectedFieldIndex) {
        return new ProtobufDeserializationException("A message closed a group with index %s, but the previously opened group had index %s".formatted(actualFieldIndex, expectedFieldIndex));
    }

    public static ProtobufDeserializationException malformedGroup() {
        return new ProtobufDeserializationException("A message opened a group but didn't close it");
    }

    public static ProtobufDeserializationException negativeLength(int size) {
        return new ProtobufDeserializationException("A message specified a negative block length for an embedded message: " + size);
    }

    public static ProtobufDeserializationException invalidFieldIndex(int index) {
        return new ProtobufDeserializationException("A message specified an invalid field index: " + index);
    }
}

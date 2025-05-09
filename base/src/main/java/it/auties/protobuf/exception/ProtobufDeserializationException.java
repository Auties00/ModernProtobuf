package it.auties.protobuf.exception;

public class ProtobufDeserializationException extends ProtobufException {
    public ProtobufDeserializationException() {
    }

    public ProtobufDeserializationException(String message) {
        super(message);
    }

    public ProtobufDeserializationException(Throwable cause) {
        super(cause);
    }

    public static ProtobufDeserializationException truncatedMessage() {
        return new ProtobufDeserializationException("A message ended unexpectedly");
    }

    public static ProtobufDeserializationException malformedVarInt() {
        return new ProtobufDeserializationException("A message contained a malformed var int");
    }

    public static ProtobufDeserializationException invalidWireType(int wireType) {
        return new ProtobufDeserializationException("A message contained an invalid wire type: %s".formatted(wireType));
    }

    public static ProtobufDeserializationException invalidEndObject(int actualFieldIndex, int expectedFieldIndex) {
        return new ProtobufDeserializationException("A message closed a group with index %s, but the previously opened group had index %s".formatted(actualFieldIndex, expectedFieldIndex));
    }

    public static ProtobufDeserializationException invalidStartObject(int fieldIndex) {
        return new ProtobufDeserializationException("A message expected a group to open at index " + fieldIndex);
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

    public static ProtobufDeserializationException reservedIndex(int index) {
        return new ProtobufDeserializationException(index + " is marked as reserved");
    }
}

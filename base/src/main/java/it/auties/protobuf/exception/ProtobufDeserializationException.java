package it.auties.protobuf.exception;

/**
 * Represents an exception that occurs during the deserialization of a Protocol Buffers message.
 * This class extends {@link ProtobufException} and provides various static methods to create
 * specific exceptions related to deserialization errors.
 */
public final class ProtobufDeserializationException extends ProtobufException {
    /**
     * Constructs a new ProtobufDeserializationException with the specified detail message.
     *
     * @param message the detail message explaining the reason for the exception
     */
    public ProtobufDeserializationException(String message) {
        super(message);
    }

    /**
     * Constructs a new ProtobufDeserializationException with the specified cause.
     *
     * @param cause the cause of the exception, which can be used to provide additional context about the error
     */
    public ProtobufDeserializationException(Throwable cause) {
        super(cause);
    }

    /**
     * Creates a new {@code ProtobufDeserializationException} indicating that a message ended unexpectedly.
     *
     * @return a {@code ProtobufDeserializationException} with a predefined message stating that the message
     *         ended unexpectedly during deserialization
     */
    public static ProtobufDeserializationException truncatedMessage() {
        return new ProtobufDeserializationException("A message ended unexpectedly");
    }

    /**
     * Creates a new {@code ProtobufDeserializationException} indicating that a message contains a malformed var int.
     *
     * @return a {@code ProtobufDeserializationException} with a predefined message stating that the var int is malformed
     */
    public static ProtobufDeserializationException malformedVarInt() {
        return new ProtobufDeserializationException("A message contained a malformed var int");
    }

    /**
     * Creates a new {@code ProtobufDeserializationException} indicating that the wire type in the serialized
     * message is invalid.
     *
     * @param wireType the invalid wire type that was encountered during deserialization
     * @return a {@code ProtobufDeserializationException} with a predefined message specifying the invalid wire type
     */
    public static ProtobufDeserializationException invalidWireType(int wireType) {
        return new ProtobufDeserializationException("A message contained an invalid wire type: %s".formatted(wireType));
    }

    /**
     * Creates a new {@code ProtobufDeserializationException} indicating that a group in a serialized protobuf
     * message was improperly terminated. This occurs when the group was closed with a field index that
     * does not match the field index of the previously opened group.
     *
     * @param actualFieldIndex   the actual field index encountered when the group was closed
     * @param expectedFieldIndex the expected field index corresponding to the previously opened group
     * @return a {@code ProtobufDeserializationException} with a detailed message explaining the mismatch
     */
    public static ProtobufDeserializationException invalidEndObject(int actualFieldIndex, int expectedFieldIndex) {
        return new ProtobufDeserializationException("A message closed a group with index %s, but the previously opened group had index %s".formatted(actualFieldIndex, expectedFieldIndex));
    }

    /**
     * Creates a new {@code ProtobufDeserializationException} indicating that a message
     * expected a group to begin but did not encounter a valid start object at the specified field index.
     *
     * @param fieldIndex the index of the field where the start of the group was expected
     * @return a {@code ProtobufDeserializationException} with a predefined message identifying the problem at the specified field index
     */
    public static ProtobufDeserializationException invalidStartObject(int fieldIndex) {
        return new ProtobufDeserializationException("A message expected a group to open at index " + fieldIndex);
    }

    /**
     * Creates a new ProtobufDeserializationException indicating that a message opened a group but failed
     * to close it. This typically happens when a well-formed group is not properly terminated in the serialized
     * protobuf data.
     *
     * @return a ProtobufDeserializationException with a predefined message describing the error
     */
    public static ProtobufDeserializationException malformedGroup() {
        return new ProtobufDeserializationException("A message opened a group but didn't close it");
    }

    /**
     * Creates a new {@code ProtobufDeserializationException} indicating that a message specified
     * a negative length for an embedded message during deserialization.
     *
     * @param size the negative length value that was encountered
     * @return a {@code ProtobufDeserializationException} with a message explaining the error
     */
    public static ProtobufDeserializationException negativeLength(int size) {
        return new ProtobufDeserializationException("A message specified a negative block length for an embedded message: " + size);
    }

    /**
     * Creates a new {@code ProtobufDeserializationException} indicating that a message specified
     * an invalid field index during deserialization.
     *
     * @param index the invalid field index that was encountered
     * @return a {@code ProtobufDeserializationException} with a detailed message specifying the invalid index
     */
    public static ProtobufDeserializationException invalidFieldIndex(int index) {
        return new ProtobufDeserializationException("A message specified an invalid field index: " + index);
    }

    /**
     * Creates a new {@code ProtobufDeserializationException} indicating that the specified field index
     * is marked as reserved in the protobuf schema and cannot be used.
     *
     * @param index the reserved index that was encountered during deserialization
     * @return a {@code ProtobufDeserializationException} with a detailed message specifying the reserved index
     */
    @SuppressWarnings("unused") // Used by ProtobufObjectDeserializationGenerator
    public static ProtobufDeserializationException reservedIndex(int index) {
        return new ProtobufDeserializationException(index + " is marked as reserved");
    }
}

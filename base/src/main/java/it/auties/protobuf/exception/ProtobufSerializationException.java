package it.auties.protobuf.exception;

/**
 * Represents an exception that occurs during the serialization of a Protocol Buffers message.
 * This class extends {@link ProtobufException} and provides static methods for creating
 * specific exceptions related to serialization errors.
 */
public final class ProtobufSerializationException extends ProtobufException {
    /**
     * Constructs a new {@code ProtobufSerializationException} with the specified detail message.
     *
     * @param message the detail message explaining the reason for the serialization exception
     */
    public ProtobufSerializationException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code ProtobufSerializationException} with the specified cause.
     *
     * @param cause the underlying cause of the exception, typically another {@link Throwable}
     *              that provides additional context about the error
     */
    public ProtobufSerializationException(Throwable cause) {
        super(cause);
    }

    /**
     * Creates a {@code ProtobufSerializationException} indicating that there is a size calculation error,
     * as space is remaining for the message after serialization.
     * This exception is intended to report underlying issues in the library, so it should hopefully be never thrown.
     *
     * @param size the remaining size indicating the mismatch
     * @return a {@code ProtobufSerializationException} with a detailed message describing the error
     */
    public static ProtobufSerializationException sizeMismatch(int size) {
        return new ProtobufSerializationException("A size calculation error occurred as there is space left for the message: " + size);
    }
}

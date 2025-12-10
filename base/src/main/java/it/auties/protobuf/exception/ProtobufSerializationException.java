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
     * Constructs a new {@code ProtobufSerializationException} with the specified detail message and cause.
     *
     * @param message the detail message explaining the reason for the serialization exception
     * @param cause the cause of the exception
     */
    public ProtobufSerializationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a {@code ProtobufSerializationException} indicating that there is a size calculation error,
     * as space is remaining for the message after serialization.
     * This exception is intended to report underlying issues in the library, so it should hopefully be never thrown.
     *
     * @return a {@code ProtobufSerializationException} with a detailed message describing the error
     */
    public static ProtobufSerializationException mismatch() {
        return new ProtobufSerializationException("A size calculation error occurred as there is space left for the message");
    }

    public static ProtobufSerializationException underflow() {
        return new ProtobufSerializationException("A size calculation error occurred as there isn't enough space space left for the message");
    }

    public static ProtobufSerializationException negativeLength() {
        return new ProtobufSerializationException("Cannot write a length delimited block with a negative length");
    }
}

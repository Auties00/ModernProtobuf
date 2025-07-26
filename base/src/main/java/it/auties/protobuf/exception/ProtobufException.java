package it.auties.protobuf.exception;

/**
 * Represents a base exception class for errors related to protocol buffer processing, such as
 * serialization, deserialization, or parsing. This exception is designed to be extended by
 * more specific exception classes to provide detailed context about various protobuf-related issues.
 */
public abstract class ProtobufException extends RuntimeException {
    /**
     * Constructs a new {@code ProtobufException} with the specified detail message.
     *
     * @param message the detail message that explains the reason for the exception
     */
    public ProtobufException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code ProtobufException} with the specified cause.
     *
     * @param cause the underlying cause of the exception, typically another {@link Throwable} that provides
     *              additional context about the error
     */
    public ProtobufException(Throwable cause) {
        super(cause);
    }
}

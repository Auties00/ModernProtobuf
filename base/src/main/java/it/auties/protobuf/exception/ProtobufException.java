package it.auties.protobuf.exception;

/**
 * Represents a base exception class for errors related to protocol buffer processing, such as
 * serialization, deserialization, or parsing. This exception is designed to be extended by
 * more specific exception classes to provide detailed context about various protobuf-related issues.
 */
public abstract class ProtobufException extends RuntimeException {
    /**
     * Constructs a new {@code ProtobufException} with the specified detail message and a cause.
     *
     * @param message the detail message that explains the reason for the exception
     * @param cause   the cause of the exception
     */
    public ProtobufException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new {@code ProtobufException} with the specified detail message.
     *
     * @param message the detail message that explains the reason for the exception
     */
    public ProtobufException(String message) {
        super(message);
    }
}

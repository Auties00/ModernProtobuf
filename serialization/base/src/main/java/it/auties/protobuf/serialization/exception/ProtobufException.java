package it.auties.protobuf.serialization.exception;

public class ProtobufException extends RuntimeException {
    public ProtobufException(String description) {
        super(description);
    }

    public ProtobufException(String message, Throwable cause) {
        super(message, cause);
    }
}

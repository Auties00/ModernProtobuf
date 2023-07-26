package it.auties.protobuf.exception;

public class ProtobufException extends RuntimeException {
    public ProtobufException(String description) {
        super(description);
    }

    public ProtobufException(String message, Throwable cause) {
        super(message, cause);
    }
}

package it.auties.protobuf.base;

public class ProtobufException extends RuntimeException {
    public ProtobufException(String description) {
        super(description);
    }

    public ProtobufException(String message, Throwable cause) {
        super(message, cause);
    }
}

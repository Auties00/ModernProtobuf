package it.auties.protobuf.exception;

public abstract class ProtobufException extends RuntimeException {
    public ProtobufException() {

    }

    public ProtobufException(String message) {
        super(message);
    }

    public ProtobufException(Throwable cause) {
        super(cause);
    }
}

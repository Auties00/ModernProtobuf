package it.auties.protobuf.serialization.exception;

public class ProtobufSerializationException extends ProtobufException {
    public ProtobufSerializationException(String description) {
        super(description);
    }

    public ProtobufSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}

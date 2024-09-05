package it.auties.protobuf.exception;

public class ProtobufSerializationException extends ProtobufException {
    public ProtobufSerializationException() {
    }

    public ProtobufSerializationException(String message) {
        super(message);
    }

    public static ProtobufSerializationException negativeUnsignedVarInt(long value) {
        return new ProtobufSerializationException("Invalid unsigned var int: " + value);
    }
}

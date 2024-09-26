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

    // Fail fast, there should never be a bug like this, but it's better to report it than fail silently
    public static ProtobufSerializationException sizeMismatch(int size) {
        return new ProtobufSerializationException("A size calculation error occurred as there is space left for the message: " + size);
    }

    public static ProtobufSerializationException unknownRawGroupFieldDefinition(int index) {
        return new ProtobufSerializationException("Missing definition in groupProperties for property with index " + index);
    }
}

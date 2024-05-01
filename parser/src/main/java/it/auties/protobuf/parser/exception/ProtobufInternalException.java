package it.auties.protobuf.parser.exception;

public class ProtobufInternalException extends ProtobufParserException {
    public ProtobufInternalException(String message, Object... args) {
        super(message, null, args);
    }
}

package it.auties.protobuf.parser.exception;

public class ProtobufInternalException extends ProtobufParserException {
    public ProtobufInternalException(String message, Object... args) {
        super(message, null, args);
    }

    public static void check(boolean condition, String message, Object... args) {
        if (condition) {
            return;
        }

        throw new ProtobufInternalException(message, args);
    }
}

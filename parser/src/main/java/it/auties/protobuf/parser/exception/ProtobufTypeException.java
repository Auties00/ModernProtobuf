package it.auties.protobuf.parser.exception;

public class ProtobufTypeException extends ProtobufParserException {
    public ProtobufTypeException(String message, Object... args) {
        super(message, null, args);
    }

    public static void check(boolean condition, String message, Object... args) {
        if (condition) {
            return;
        }

        throw new ProtobufTypeException(message, args);
    }
}

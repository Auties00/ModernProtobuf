package it.auties.protobuf.parser.exception;

public class ProtobufSyntaxException extends ProtobufParserException {
    public ProtobufSyntaxException(String message, int line, Object... args) {
        super(message, line, args);
    }

    public static void check(boolean condition, String message, int line, Object... args) {
        if (condition) {
            return;
        }

        throw new ProtobufSyntaxException(message, line, args);
    }
}

package it.auties.protobuf.parser.exception;

import it.auties.protobuf.exception.ProtobufException;

public class ProtobufParserException extends ProtobufException {
    public ProtobufParserException(String message, Integer line, Object... args) {
        this(line == null ? formatMessage(message, args) : formatMessage(message, args) + " on line " + line);
    }

    public ProtobufParserException(String message) {
        super(message);
    }

    public static void check(boolean condition, String message, int line, Object... args) {
        if (condition) {
            return;
        }

        throw new ProtobufParserException(message, line, args);
    }

    private static String formatMessage(String message, Object[] args) {
        return message == null ? null : message.formatted(args);
    }
}

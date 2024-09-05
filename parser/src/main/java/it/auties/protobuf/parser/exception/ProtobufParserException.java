package it.auties.protobuf.parser.exception;

public class ProtobufParserException extends RuntimeException {
    ProtobufParserException(String message, Integer line, Object... args) {
        this(line == null ? formatMessage(message, args) : formatMessage(message, args) + " on line " + line);
    }

    ProtobufParserException(String message) {
        super(message);
    }

    private static String formatMessage(String message, Object[] args) {
        return message == null ? null : message.formatted(args);
    }
}

package it.auties.protobuf.parser.exception;

public class ProtobufParserException extends RuntimeException {
    ProtobufParserException(String message, Integer line, Object... args) {
        super(line == null ? formatMessage(message, args) : formatMessage(message, args) + " on line " + line);
    }

    private static String formatMessage(String message, Object[] args) {
        return message == null ? null : message.formatted(args);
    }
}

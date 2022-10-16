package it.auties.protobuf.parser.exception;

public class ProtobufTypeException extends IllegalArgumentException {
    public ProtobufTypeException(String message, Object... args) {
        super(formatMessage(message, args));
    }

    private static String formatMessage(String message, Object[] args) {
        return message == null ? null : message.formatted(args);
    }
}

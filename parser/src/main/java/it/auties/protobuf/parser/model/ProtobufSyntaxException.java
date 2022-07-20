package it.auties.protobuf.parser.model;

public class ProtobufSyntaxException extends IllegalArgumentException {
    public ProtobufSyntaxException(String message, int line, Object... args) {
        super("%s at line %s".formatted(formatMessage(message, args), line));
    }

    private static String formatMessage(String message, Object[] args) {
        return message == null ? null : message.formatted(args);
    }

    public static void check(boolean condition, String message, int line, Object... args) {
        if (condition) {
            return;
        }

        throw new ProtobufSyntaxException(message, line, args);
    }
}

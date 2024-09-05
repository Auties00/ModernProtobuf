package it.auties.protobuf.parser.exception;

import java.nio.file.Path;

public class ProtobufSyntaxException extends ProtobufParserException {
    public ProtobufSyntaxException(String message, int line, Object... args) {
        super(message, line, args);
    }

    private ProtobufSyntaxException(String message) {
        super(message);
    }

    public static void check(boolean condition, String message, int line, Object... args) {
        if (condition) {
            return;
        }

        throw new ProtobufSyntaxException(message, line, args);
    }

    public static ProtobufSyntaxException withPath(String message, Path location) {
        return new ProtobufSyntaxException(message + (location == null ? " while parsing string" : " in file " + location));
    }
}

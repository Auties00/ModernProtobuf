package it.auties.protobuf.parser;

import it.auties.protobuf.exception.ProtobufException;
import it.auties.protobuf.parser.tree.ProtobufOptionStatement;
import it.auties.protobuf.parser.type.ProtobufPrimitiveTypeReference;

import java.nio.file.Path;

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

    public static ProtobufParserException wrap(ProtobufParserException exception, Path location) {
        var withPath = new ProtobufParserException(exception.getMessage() + " while parsing " + location);
        withPath.setStackTrace(exception.getStackTrace());
        return withPath;
    }

    public static ProtobufParserException invalidOption(ProtobufOptionStatement option, ProtobufPrimitiveTypeReference primitiveType) {
        return new ProtobufParserException("Invalid value " + option.value() + " for type " + primitiveType.protobufType().name().toLowerCase() + " in option " + option.name());
    }

    private static String formatMessage(String message, Object[] args) {
        return message == null ? null : message.formatted(args);
    }
}

package it.auties.protobuf.parser.exception;

import it.auties.protobuf.exception.ProtobufException;

/**
 * Represents a base exception class for errors encountered during Protocol Buffer parsing.
 * <p>
 * This exception serves as the parent class for all parsing-related exceptions in the parser module,
 * including lexical analysis errors ({@link ProtobufLexerException}), syntax errors ({@link ProtobufSyntaxException}),
 * and semantic validation errors ({@link ProtobufSemanticException}).
 * </p>
 * <p>
 * The exception provides automatic line number formatting in error messages when a line number is provided,
 * making it easier to identify the location of errors in Protocol Buffer definition files.
 * </p>
 */
public class ProtobufParserException extends ProtobufException {
    /**
     * Constructs a new {@code ProtobufParserException} with a formatted message and optional line number.
     * <p>
     * If a line number is provided, it will be appended to the formatted message.
     * The message supports format strings with placeholders that will be replaced by the provided arguments.
     * </p>
     *
     * @param message the format string for the error message
     * @param line the line number where the error occurred, or null if not applicable
     * @param args the arguments to be formatted into the message
     */
    public ProtobufParserException(String message, Integer line, Object... args) {
        this(line == null ? formatMessage(message, args) : formatMessage(message, args) + " on line " + line);
    }

    private static String formatMessage(String message, Object[] args) {
        return message == null ? null : message.formatted(args);
    }

    /**
     * Constructs a new {@code ProtobufParserException} with the specified detail message.
     *
     * @param message the detail message that explains the reason for the exception
     */
    public ProtobufParserException(String message) {
        super(message);
    }
}

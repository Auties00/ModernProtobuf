package it.auties.protobuf.parser.exception;

/**
 * Represents an exception thrown when syntax errors are encountered during Protocol Buffer parsing.
 * <p>
 * Syntax exceptions occur when the Protocol Buffer definition violates the grammatical rules
 * of the Protocol Buffer language. Examples include:
 * </p>
 * <ul>
 *   <li>Missing required keywords or semicolons</li>
 *   <li>Malformed message or field declarations</li>
 *   <li>Unexpected tokens in the input stream</li>
 *   <li>Incorrectly nested blocks or braces</li>
 *   <li>Invalid field option syntax</li>
 * </ul>
 * <p>
 * This exception provides a static {@link #check(boolean, String, int, Object...)} method for convenient
 * validation of syntax constraints during parsing.
 * </p>
 */
public class ProtobufSyntaxException extends ProtobufParserException {
    /**
     * Constructs a new {@code ProtobufSyntaxException} with a formatted message and optional line number.
     *
     * @param message the format string for the error message
     * @param line the line number where the syntax error occurred, or null if not applicable
     * @param args the arguments to be formatted into the message
     */
    public ProtobufSyntaxException(String message, Integer line, Object... args) {
        super(message, line, args);
    }

    /**
     * Constructs a new {@code ProtobufSyntaxException} with the specified cause.
     *
     * @param throwable the underlying cause of the exception
     */
    public ProtobufSyntaxException(Throwable throwable) {
        super(throwable);
    }

    /**
     * Constructs a new {@code ProtobufSyntaxException} with the specified detail message.
     *
     * @param message the detail message that explains the reason for the exception
     */
    public ProtobufSyntaxException(String message) {
        super(message);
    }

    /**
     * Validates a syntax constraint and throws an exception if the condition is not met.
     * <p>
     * This is a convenience method for syntax validation during parsing. If the condition is false,
     * a {@code ProtobufSyntaxException} is thrown with the formatted message and line number.
     * </p>
     *
     * @param condition the condition to check; if false, an exception is thrown
     * @param message the format string for the error message
     * @param line the line number where the syntax error occurred
     * @param args the arguments to be formatted into the message
     * @throws ProtobufSyntaxException if the condition is false
     */
    public static void check(boolean condition, String message, int line, Object... args) {
        if (condition) {
            return;
        }

        throw new ProtobufSyntaxException(message, line, args);
    }
}

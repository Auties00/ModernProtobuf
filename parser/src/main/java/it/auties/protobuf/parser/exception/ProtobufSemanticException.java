package it.auties.protobuf.parser.exception;

/**
 * Represents an exception thrown when semantic validation errors are encountered during Protocol Buffer parsing.
 * <p>
 * Semantic exceptions occur when the Protocol Buffer definition is syntactically correct but violates
 * semantic rules of the language. Examples include:
 * </p>
 * <ul>
 *   <li>Duplicate field numbers within a message</li>
 *   <li>Reserved field numbers being used</li>
 *   <li>Invalid type references</li>
 *   <li>Conflicting field or message names</li>
 *   <li>Invalid option values</li>
 * </ul>
 * <p>
 * This exception provides a static {@link #check(boolean, String, int, Object...)} method for convenient
 * validation of semantic constraints during parsing.
 * </p>
 */
public class ProtobufSemanticException extends ProtobufParserException {
    /**
     * Constructs a new {@code ProtobufSemanticException} with a formatted message and optional line number.
     *
     * @param message the format string for the error message
     * @param line the line number where the semantic error occurred, or null if not applicable
     * @param args the arguments to be formatted into the message
     */
    public ProtobufSemanticException(String message, Integer line, Object... args) {
        super(message, line, args);
    }

    /**
     * Constructs a new {@code ProtobufSemanticException} with the specified cause.
     *
     * @param throwable the underlying cause of the exception
     */
    public ProtobufSemanticException(Throwable throwable) {
        super(throwable);
    }

    /**
     * Constructs a new {@code ProtobufSemanticException} with the specified detail message.
     *
     * @param message the detail message that explains the reason for the exception
     */
    public ProtobufSemanticException(String message) {
        super(message);
    }

    /**
     * Validates a semantic constraint and throws an exception if the condition is not met.
     * <p>
     * This is a convenience method for semantic validation during parsing. If the condition is false,
     * a {@code ProtobufSemanticException} is thrown with the formatted message and line number.
     * </p>
     *
     * @param condition the condition to check; if false, an exception is thrown
     * @param message the format string for the error message
     * @param line the line number where the semantic error occurred
     * @param args the arguments to be formatted into the message
     * @throws ProtobufSemanticException if the condition is false
     */
    public static void check(boolean condition, String message, int line, Object... args) {
        if (condition) {
            return;
        }

        throw new ProtobufSemanticException(message, line, args);
    }
}

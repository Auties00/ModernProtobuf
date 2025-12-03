package it.auties.protobuf.parser.exception;

/**
 * Represents an exception thrown when lexical analysis errors are encountered during Protocol Buffer tokenization.
 * <p>
 * Lexer exceptions occur during the tokenization phase when the input cannot be broken down into valid tokens.
 * Examples include:
 * </p>
 * <ul>
 *   <li>Invalid character sequences that don't form valid tokens</li>
 *   <li>Malformed numeric literals</li>
 *   <li>Unterminated string literals</li>
 *   <li>Invalid escape sequences in strings</li>
 *   <li>Unexpected end of input while reading a token</li>
 * </ul>
 * <p>
 * This exception provides a static {@link #check(boolean, String, int, Object...)} method for convenient
 * validation during the lexical analysis phase.
 * </p>
 */
public class ProtobufLexerException extends ProtobufParserException {
    /**
     * Constructs a new {@code ProtobufLexerException} with the specified detail message.
     *
     * @param message the detail message that explains the reason for the exception
     */
    public ProtobufLexerException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code ProtobufLexerException} with a formatted message and optional line number.
     *
     * @param message the format string for the error message
     * @param line the line number where the lexical error occurred, or null if not applicable
     * @param args the arguments to be formatted into the message
     */
    public ProtobufLexerException(String message, Integer line, Object... args) {
        super(message, line, args);
    }

    /**
     * Validates a lexical constraint and throws an exception if the condition is not met.
     * <p>
     * This is a convenience method for validation during lexical analysis. If the condition is false,
     * a {@code ProtobufLexerException} is thrown with the formatted message and line number.
     * </p>
     *
     * @param condition the condition to check; if false, an exception is thrown
     * @param message the format string for the error message
     * @param line the line number where the lexical error occurred
     * @param args the arguments to be formatted into the message
     * @throws ProtobufLexerException if the condition is false
     */
    public static void check(boolean condition, String message, int line, Object... args) {
        if (condition) {
            return;
        }

        throw new ProtobufLexerException(message, line, args);
    }
}

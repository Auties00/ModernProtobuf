package it.auties.protobuf.parser.token;

import java.util.Objects;

/**
 * Represents a string literal token produced by the Protocol Buffer lexer.
 * <p>
 * String literal tokens represent quoted text values in Protocol Buffer definitions. The Protocol Buffer
 * language supports both double-quoted ({@code "}) and single-quoted ({@code '}) string literals.
 * Adjacent string literals are automatically concatenated by the lexer.
 * </p>
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Single string literal
 * option java_package = "com.example.proto";
 *
 * // Multiple adjacent literals (concatenated by lexer)
 * option annotation = "This is a "
 *                     "multi-line "
 *                     "annotation";
 * }</pre>
 *
 * @param value the string value without the surrounding delimiters, must not be null
 * @param delimiter the quote character used to delimit this literal (either {@code "} or {@code '})
 */
public record ProtobufLiteralToken(String value, char delimiter) implements ProtobufToken {
    public ProtobufLiteralToken {
        Objects.requireNonNull(value, "value cannot be null");
    }

    @Override
    public String toString() {
        return delimiter + value + delimiter;
    }
}

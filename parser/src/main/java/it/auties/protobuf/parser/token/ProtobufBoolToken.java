package it.auties.protobuf.parser.token;

/**
 * Represents a boolean literal token produced by the Protocol Buffer lexer.
 * <p>
 * Boolean tokens represent the literal values {@code true} and {@code false} as defined
 * in the Protocol Buffer language specification. These tokens are used in option values,
 * default values, and other contexts where boolean literals are permitted.
 * </p>
 *
 * @param value the boolean value represented by this token
 */
public record ProtobufBoolToken(boolean value) implements ProtobufToken {
    @Override
    public String toString() {
        return value ? "true" : "false";
    }
}

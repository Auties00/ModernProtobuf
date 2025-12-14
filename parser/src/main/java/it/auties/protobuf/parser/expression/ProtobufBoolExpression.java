package it.auties.protobuf.parser.expression;

/**
 * Represents a boolean literal expression in the Protocol Buffer AST.
 * <p>
 * Boolean expressions represent the literal values {@code true} and {@code false} as they
 * appear in Protocol Buffer definitions, typically used in:
 * </p>
 * <ul>
 *   <li>Default values for boolean fields</li>
 *   <li>Option values ({@code [packed = true]})</li>
 *   <li>Custom option assignments</li>
 * </ul>
 *
 * @see ProtobufExpression
 */
public record ProtobufBoolExpression(boolean value) implements ProtobufExpression {
    @Override
    public String toString() {
        return String.valueOf(value);
    }
}

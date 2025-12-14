package it.auties.protobuf.parser.expression;

import java.util.Objects;

/**
 * Represents a string literal expression in the Protocol Buffer AST.
 * <p>
 * String literal expressions represent quoted text values as they appear in Protocol Buffer
 * definitions. They can be used in:
 * </p>
 * <ul>
 *   <li>Default values for string fields</li>
 *   <li>Option values ({@code [deprecated_message = "Use X instead"]})</li>
 *   <li>Reserved field names ({@code reserved "old_field", "deprecated_field"})</li>
 *   <li>Custom message annotations</li>
 * </ul>
 * <p>
 * This class implements both {@link ProtobufExpression} and {@link ProtobufReservedExpression},
 * allowing it to be used in reserved statements for field names.
 * </p>
 *
 * @see ProtobufExpression
 * @see ProtobufReservedExpression
 */
public record ProtobufLiteralExpression(String value) implements ProtobufExpression, ProtobufReservedExpression {
    public ProtobufLiteralExpression {
        Objects.requireNonNull(value, "value cannot be null");
    }

    @Override
    public String toString() {
        return "\"" + value + "\"";
    }
}

package it.auties.protobuf.parser.expression;

import it.auties.protobuf.parser.number.ProtobufFloatingPoint;
import it.auties.protobuf.parser.number.ProtobufNumber;

import java.util.Objects;

/**
 * Represents a floating-point literal expression in the Protocol Buffer AST.
 * <p>
 * Floating-point expressions represent decimal values with arbitrary precision, supporting:
 * </p>
 * <ul>
 *   <li>Floating-point literals (e.g., {@code 3.14}, {@code 1.0e-10})</li>
 *   <li>Special floating-point values ({@code inf}, {@code -inf}, {@code nan})</li>
 * </ul>
 * <p>
 * This expression type is typically used in:
 * </p>
 * <ul>
 *   <li>Default values for {@code float} and {@code double} fields</li>
 *   <li>Option values with floating-point types</li>
 * </ul>
 *
 * @see ProtobufNumber
 */
public record ProtobufFloatingPointExpression(ProtobufFloatingPoint value) implements ProtobufNumberExpression {
    public ProtobufFloatingPointExpression {
        Objects.requireNonNull(value, "value cannot be null");
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
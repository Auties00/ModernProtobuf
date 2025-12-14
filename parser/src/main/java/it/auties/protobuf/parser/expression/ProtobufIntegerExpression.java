package it.auties.protobuf.parser.expression;

import it.auties.protobuf.parser.number.ProtobufInteger;
import it.auties.protobuf.parser.number.ProtobufNumber;

import java.util.Objects;

/**
 * Represents an integer literal expression in the Protocol Buffer AST.
 * <p>
 * Integer expressions represent whole number values with arbitrary precision, supporting:
 * </p>
 * <ul>
 *   <li>Decimal literals (e.g., {@code 123})</li>
 *   <li>Hexadecimal literals (e.g., {@code 0x7F})</li>
 *   <li>Octal literals (e.g., {@code 0755})</li>
 * </ul>
 * <p>
 * This class implements both {@link ProtobufExtensionsExpression} and {@link ProtobufReservedExpression},
 * allowing it to be used in:
 * </p>
 * <ul>
 *   <li>Extension range declarations ({@code extensions 100})</li>
 *   <li>Reserved field number declarations ({@code reserved 1, 2, 3})</li>
 *   <li>Default values for integer fields</li>
 *   <li>Enum constant values</li>
 *   <li>Option values with integer types</li>
 * </ul>
 *
 * @see ProtobufNumber
 * @see ProtobufExtensionsExpression
 * @see ProtobufReservedExpression
 */
public record ProtobufIntegerExpression(ProtobufInteger value) implements ProtobufExtensionsExpression, ProtobufReservedExpression, ProtobufNumberExpression {
    public ProtobufIntegerExpression {
        Objects.requireNonNull(value, "value cannot be null");
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
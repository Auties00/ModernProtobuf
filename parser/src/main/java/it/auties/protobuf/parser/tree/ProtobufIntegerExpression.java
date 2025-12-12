package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.type.ProtobufInteger;
import it.auties.protobuf.parser.type.ProtobufNumber;

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
public final class ProtobufIntegerExpression
        extends ProtobufExpressionImpl
        implements ProtobufExtensionsExpression, ProtobufReservedExpression, ProtobufNumberExpression {
    private ProtobufInteger value;

    /**
     * Constructs a new integer expression at the specified line number.
     *
     * @param line the line number in the source file
     */
    public ProtobufIntegerExpression(int line) {
        super(line);
    }

    /**
     * Returns the integer value of this expression.
     *
     * @return the {@link ProtobufInteger}, or null if not yet set
     */
    public ProtobufInteger value() {
        return value;
    }

    /**
     * Sets the integer value for this expression.
     *
     * @param value the integer value to set
     */
    public void setValue(ProtobufInteger value) {
        this.value = value;
    }

    /**
     * Checks whether this expression has a value assigned.
     *
     * @return true if a value is present, false otherwise
     */
    public boolean hasValue() {
        return value != null;
    }

    @Override
    public boolean isAttributed() {
        return hasValue();
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
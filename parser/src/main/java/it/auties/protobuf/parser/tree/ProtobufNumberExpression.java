package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.type.ProtobufNumber;

/**
 * Represents a numeric literal expression in the Protocol Buffer AST.
 * <p>
 * Number expressions represent numeric values with arbitrary precision, supporting:
 * </p>
 * <ul>
 *   <li>Integer literals (decimal, hexadecimal, octal)</li>
 *   <li>Floating-point literals</li>
 *   <li>Special floating-point values ({@code inf}, {@code -inf}, {@code nan})</li>
 * </ul>
 * <p>
 * This class implements both {@link ProtobufExtensionsExpression} and {@link ProtobufReservedExpression},
 * allowing it to be used in:
 * </p>
 * <ul>
 *   <li>Extension range declarations ({@code extensions 100})</li>
 *   <li>Reserved field number declarations ({@code reserved 1, 2, 3})</li>
 *   <li>Default values for numeric fields</li>
 *   <li>Enum constant values</li>
 *   <li>Option values</li>
 * </ul>
 *
 * @see ProtobufNumber
 * @see ProtobufExtensionsExpression
 * @see ProtobufReservedExpression
 */
public final class ProtobufNumberExpression
        extends ProtobufExpressionImpl
        implements ProtobufExtensionsExpression, ProtobufReservedExpression {
    private ProtobufNumber value;

    /**
     * Constructs a new numeric expression at the specified line number.
     *
     * @param line the line number in the source file
     */
    public ProtobufNumberExpression(int line) {
        super(line);
    }

    /**
     * Returns the numeric value of this expression.
     *
     * @return the {@link ProtobufNumber}, or null if not yet set
     */
    public ProtobufNumber value() {
        return value;
    }

    /**
     * Sets the numeric value for this expression.
     *
     * @param value the numeric value to set
     */
    public void setValue(ProtobufNumber value) {
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
        return value != null;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}

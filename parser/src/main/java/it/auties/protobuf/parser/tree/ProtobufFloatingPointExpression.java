package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.type.ProtobufFloatingPoint;
import it.auties.protobuf.parser.type.ProtobufNumber;

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
public final class ProtobufFloatingPointExpression
        extends ProtobufExpressionImpl implements ProtobufNumberExpression {
    private ProtobufFloatingPoint value;

    /**
     * Constructs a new floating-point expression at the specified line number.
     *
     * @param line the line number in the source file
     */
    public ProtobufFloatingPointExpression(int line) {
        super(line);
    }

    /**
     * Returns the floating-point value of this expression.
     *
     * @return the {@link ProtobufNumber}, or null if not yet set
     */
    public ProtobufFloatingPoint value() {
        return value;
    }

    /**
     * Sets the floating-point value for this expression.
     *
     * @param value the floating-point value to set
     */
    public void setValue(ProtobufFloatingPoint value) {
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
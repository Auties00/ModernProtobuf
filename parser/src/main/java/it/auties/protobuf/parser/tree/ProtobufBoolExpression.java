package it.auties.protobuf.parser.tree;

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
public final class ProtobufBoolExpression
        extends ProtobufExpressionImpl
        implements ProtobufExpression {
    private Boolean value;

    /**
     * Constructs a new boolean expression at the specified line number.
     *
     * @param line the line number in the source file
     */
    public ProtobufBoolExpression(int line) {
        super(line);
    }

    /**
     * Returns the boolean value of this expression.
     *
     * @return the boolean value, or null if not yet set
     */
    public Boolean value() {
        return value;
    }

    /**
     * Checks whether this expression has a value assigned.
     *
     * @return true if a value is present, false otherwise
     */
    public boolean hasValue() {
        return value != null;
    }

    /**
     * Sets the boolean value for this expression.
     *
     * @param value the boolean value to set
     */
    public void setValue(Boolean value) {
        this.value = value;
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

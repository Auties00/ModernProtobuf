package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.type.ProtobufRange;

/**
 * Represents an integer range expression in the Protocol Buffer AST.
 * <p>
 * Integer range expressions specify contiguous blocks of field numbers using the
 * {@code to} keyword. They can be bounded (with explicit minimum and maximum) or
 * lower-bounded (extending to the maximum allowed field number).
 * </p>
 * <h2>Examples:</h2>
 * <pre>{@code
 * message Example {
 *   reserved 10 to 20;          // Bounded range
 *   reserved 1000 to max;       // Lower-bounded range
 *   extensions 100 to 200;      // Extension range
 * }
 * }</pre>
 * <p>
 * This class implements both {@link ProtobufExtensionsExpression} and {@link ProtobufReservedExpression},
 * allowing it to be used in both extension and reserved statements.
 * </p>
 *
 * @see ProtobufRange
 * @see ProtobufExtensionsExpression
 * @see ProtobufReservedExpression
 */
public final class ProtobufIntegerRangeExpression
        extends ProtobufExpressionImpl
        implements ProtobufExpression, ProtobufExtensionsExpression, ProtobufReservedExpression {
    private ProtobufRange value;

    /**
     * Constructs a new integer range expression at the specified line number.
     *
     * @param line the line number in the source file
     */
    public ProtobufIntegerRangeExpression(int line) {
        super(line);
    }

    /**
     * Returns the range value.
     *
     * @return the {@link ProtobufRange}, or null if not yet set
     */
    public ProtobufRange value() {
        return value;
    }

    /**
     * Sets the range value.
     *
     * @param value the range to set
     */
    public void setValue(ProtobufRange value) {
        this.value = value;
    }

    @Override
    public boolean isAttributed() {
        return value != null;
    }

    @Override
    public String toString() {
        return value == null ? "[unknown]" : value.toString();
    }
}

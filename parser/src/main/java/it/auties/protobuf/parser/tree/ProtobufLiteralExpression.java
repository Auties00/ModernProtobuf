package it.auties.protobuf.parser.tree;

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
public final class ProtobufLiteralExpression
        extends ProtobufExpressionImpl
        implements ProtobufExpression, ProtobufReservedExpression {
    private String value;

    /**
     * Constructs a new string literal expression at the specified line number.
     *
     * @param line the line number in the source file
     */
    public ProtobufLiteralExpression(int line) {
        super(line);
    }

    /**
     * Returns the string value of this literal.
     *
     * @return the string value (without surrounding quotes), or null if not yet set
     */
    public String value() {
        return value;
    }

    /**
     * Checks whether this literal has a value assigned.
     *
     * @return true if a value is present, false otherwise
     */
    public boolean hasValue() {
        return value != null;
    }

    /**
     * Sets the string value for this literal.
     *
     * @param value the string value to set (without surrounding quotes)
     */
    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public boolean isAttributed() {
        return value != null;
    }

    @Override
    public String toString() {
        return "\"" + value + "\"";
    }
}

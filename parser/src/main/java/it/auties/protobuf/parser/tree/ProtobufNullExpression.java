package it.auties.protobuf.parser.tree;

/**
 * Represents a null value expression in the Protocol Buffer AST.
 * <p>
 * Null expressions represent the absence of a value or an explicitly null value in Protocol
 * Buffer definitions. This is primarily used in message literal syntax and option values.
 * </p>
 * <p>
 * Unlike other expressions, null expressions are always attributed and have no mutable state.
 * </p>
 *
 * @see ProtobufExpression
 */
public final class ProtobufNullExpression
        extends ProtobufExpressionImpl
        implements ProtobufExpression {
    /**
     * Constructs a new null expression at the specified line number.
     *
     * @param line the line number in the source file
     */
    public ProtobufNullExpression(int line) {
        super(line);
    }

    @Override
    public boolean isAttributed() {
        return true;
    }

    @Override
    public String toString() {
        return "null";
    }
}

package it.auties.protobuf.parser.expression;

/**
 * Represents a null value expression in the Protocol Buffer AST.
 * <p>
 * Null expressions represent the absence of a value or an explicitly null value in Protocol
 * Buffer definitions. This is primarily used in message literal syntax and option values.
 * </p>
 * </p>
 *
 * @see ProtobufExpression
 */
public record ProtobufNullExpression() implements ProtobufExpression {
    @Override
    public String toString() {
        return "null";
    }
}

package it.auties.protobuf.parser.expression;

import it.auties.protobuf.parser.number.ProtobufIntegerRange;

import java.util.Objects;

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
 * @see ProtobufIntegerRange
 * @see ProtobufExtensionsExpression
 * @see ProtobufReservedExpression
 */
public record ProtobufIntegerRangeExpression(ProtobufIntegerRange value) implements ProtobufExpression, ProtobufExtensionsExpression, ProtobufReservedExpression {
    public ProtobufIntegerRangeExpression {
        Objects.requireNonNull(value, "value cannot be null");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

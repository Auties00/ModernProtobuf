package it.auties.protobuf.parser.tree;

/**
 * Marker interface for expressions that can appear in {@code reserved} statements.
 * <p>
 * Reserved statements prevent field numbers or names from being used in a message or enum,
 * typically to maintain backward compatibility when removing fields. This sealed interface
 * restricts which expression types can be used:
 * </p>
 * <ul>
 *   <li>{@link ProtobufIntegerExpression} - Reserved field numbers ({@code reserved 1, 2, 3})</li>
 *   <li>{@link ProtobufIntegerRangeExpression} - Reserved field number ranges ({@code reserved 10 to 20})</li>
 *   <li>{@link ProtobufLiteralExpression} - Reserved field names ({@code reserved "old_field", "deprecated"})</li>
 * </ul>
 * <h2>Example:</h2>
 * <pre>{@code
 * message Example {
 *   reserved 2, 15, 9 to 11;              // Reserved numbers and range
 *   reserved "foo", "bar", "deprecated_*"; // Reserved names
 * }
 * }</pre>
 *
 * @see ProtobufReservedStatement
 */
public sealed interface ProtobufReservedExpression
        extends ProtobufExpression
        permits ProtobufIntegerExpression, ProtobufIntegerRangeExpression, ProtobufLiteralExpression {
}

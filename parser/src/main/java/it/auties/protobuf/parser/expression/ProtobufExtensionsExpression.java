package it.auties.protobuf.parser.expression;

import it.auties.protobuf.parser.tree.ProtobufExtensionsStatement;

/**
 * Marker interface for expressions that can appear in {@code extensions} statements.
 * <p>
 * Extensions statements declare ranges of field numbers that can be used by extension fields
 * defined in other files. This sealed interface restricts which expression types can be used:
 * </p>
 * <ul>
 *   <li>{@link ProtobufIntegerExpression} - Single extension field number</li>
 *   <li>{@link ProtobufIntegerRangeExpression} - Extension field number ranges</li>
 * </ul>
 * <h2>Example:</h2>
 * <pre>{@code
 * message Example {
 *   extensions 100 to 199;    // Range
 *   extensions 1000 to max;   // Unbounded range
 * }
 * }</pre>
 *
 * @see ProtobufExtensionsStatement
 */
public sealed interface ProtobufExtensionsExpression
        extends ProtobufExpression
        permits ProtobufIntegerExpression, ProtobufIntegerRangeExpression {
}

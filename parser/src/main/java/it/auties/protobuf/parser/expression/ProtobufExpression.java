package it.auties.protobuf.parser.expression;

import it.auties.protobuf.parser.tree.*;

/**
 * Represents an expression in the Protocol Buffer abstract syntax tree.
 * <p>
 * Expressions are values and value-like constructs that appear in Protocol Buffer definitions.
 * Unlike {@link ProtobufStatement statements}, expressions represent values rather than declarations.
 * They are used in various contexts including:
 * </p>
 * <ul>
 *   <li>Default values for fields</li>
 *   <li>Option values ({@code [packed = true]})</li>
 *   <li>Enum constant values ({@code UNKNOWN = 0})</li>
 *   <li>Range specifications ({@code 1 to 10}, {@code reserved 1, 2, 3})</li>
 *   <li>Extension ranges ({@code extensions 100 to 200})</li>
 * </ul>
 * <p>
 * Expression types include:
 * </p>
 * <ul>
 *   <li>{@link ProtobufLiteralExpression} - String literals</li>
 *   <li>{@link ProtobufNumberExpression} - Numeric literals</li>
 *   <li>{@link ProtobufBoolExpression} - Boolean literals</li>
 *   <li>{@link ProtobufNullExpression} - Null values</li>
 *   <li>{@link ProtobufJsonExpression} - JSON literal values</li>
 *   <li>{@link ProtobufEnumConstantExpression} - Enum constant references</li>
 *   <li>{@link ProtobufOptionExpression} - Option key-value pairs</li>
 *   <li>{@link ProtobufIntegerRangeExpression} - Numeric ranges</li>
 *   <li>{@link ProtobufReservedExpression} - Reserved field specifications</li>
 *   <li>{@link ProtobufExtensionsExpression} - Extension range specifications</li>
 * </ul>
 *
 * @see ProtobufStatement
 * @see ProtobufTree
 */
public sealed interface ProtobufExpression
        permits ProtobufBoolExpression, ProtobufEnumConstantExpression, ProtobufExtensionsExpression, ProtobufJsonExpression, ProtobufIntegerRangeExpression, ProtobufLiteralExpression, ProtobufNullExpression, ProtobufNumberExpression, ProtobufOptionExpression, ProtobufReservedExpression {

}

package it.auties.protobuf.parser.expression;

/**
 * Represents a numeric literal expression in the Protocol Buffer AST.
 * <p>
 * This sealed interface serves as the common type for all numeric expressions,
 * encompassing both integer and floating-point values. It permits two implementations:
 * </p>
 * <ul>
 *   <li>{@link ProtobufIntegerExpression} - for integer literals (decimal, hexadecimal, octal)</li>
 *   <li>{@link ProtobufFloatingPointExpression} - for floating-point literals and special values (inf, nan)</li>
 * </ul>
 * <p>
 * Numeric expressions appear in various contexts within Protocol Buffer definitions:
 * </p>
 * <ul>
 *   <li>Default values for numeric fields</li>
 *   <li>Option values</li>
 *   <li>Enum constant values (integers only)</li>
 *   <li>Extension and reserved range declarations (integers only)</li>
 * </ul>
 *
 * @see ProtobufIntegerExpression
 * @see ProtobufFloatingPointExpression
 */
public sealed interface ProtobufNumberExpression
        extends ProtobufExpression
        permits ProtobufIntegerExpression, ProtobufFloatingPointExpression {
}
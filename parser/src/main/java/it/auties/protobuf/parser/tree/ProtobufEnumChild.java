package it.auties.protobuf.parser.tree;

/**
 * Marker interface for statements that can appear inside a Protocol Buffer enum definition.
 * <p>
 * This sealed interface restricts which statement types can be children of a
 * {@link ProtobufEnumStatement}. Enum-level statements include:
 * </p>
 * <ul>
 *   <li>{@link ProtobufEnumConstantStatement} - Enum constant declarations</li>
 *   <li>{@link ProtobufOptionStatement} - Enum-level options</li>
 *   <li>{@link ProtobufReservedStatement} - Reserved value declarations</li>
 *   <li>{@link ProtobufEmptyStatement} - Empty statements</li>
 * </ul>
 *
 * @see ProtobufEnumStatement
 */
public sealed interface ProtobufEnumChild
        extends ProtobufStatement
        permits ProtobufEmptyStatement, ProtobufEnumConstantStatement, ProtobufOptionStatement, ProtobufReservedStatement {

}

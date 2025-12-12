package it.auties.protobuf.parser.tree;

/**
 * Marker interface for statements that can appear inside a Protocol Buffer oneof block.
 * <p>
 * This sealed interface restricts which statement types can be children of a
 * {@link ProtobufOneofFieldStatement}. Oneof blocks ensure that only one field is set at a time.
 * Permitted statements include:
 * </p>
 * <ul>
 *   <li>{@link ProtobufFieldStatement} - Field declarations within the oneof</li>
 *   <li>{@link ProtobufGroupFieldStatement} - Group fields (deprecated)</li>
 *   <li>{@link ProtobufOptionStatement} - Oneof-level options</li>
 *   <li>{@link ProtobufEmptyStatement} - Empty statements</li>
 * </ul>
 * <p>
 * Note: Oneof fields cannot be repeated, required, or optional in proto3.
 * </p>
 *
 * @see ProtobufOneofFieldStatement
 */
public sealed interface ProtobufOneofChild
        extends ProtobufStatement
        permits ProtobufFieldStatement, ProtobufGroupFieldStatement, ProtobufOptionStatement {

}

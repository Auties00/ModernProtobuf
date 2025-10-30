package it.auties.protobuf.parser.tree;

/**
 * Marker interface for statements that can appear inside a Protocol Buffer message definition.
 * <p>
 * This sealed interface restricts which statement types can be children of a
 * {@link ProtobufMessageStatement}. Message-level statements include:
 * </p>
 * <ul>
 *   <li>{@link ProtobufFieldStatement} - Field declarations</li>
 *   <li>{@link ProtobufOneofFieldStatement} - Oneof blocks</li>
 *   <li>{@link ProtobufGroupFieldStatement} - Group fields (deprecated)</li>
 *   <li>{@link ProtobufMessageStatement} - Nested message definitions</li>
 *   <li>{@link ProtobufEnumStatement} - Nested enum definitions</li>
 *   <li>{@link ProtobufExtendStatement} - Extend blocks</li>
 *   <li>{@link ProtobufExtensionsStatement} - Extension range declarations</li>
 *   <li>{@link ProtobufReservedStatement} - Reserved field declarations</li>
 *   <li>{@link ProtobufOptionStatement} - Message-level options</li>
 *   <li>{@link ProtobufEmptyStatement} - Empty statements</li>
 * </ul>
 *
 * @see ProtobufMessageStatement
 */
public sealed interface ProtobufMessageChild
        extends ProtobufStatement
        permits ProtobufEmptyStatement, ProtobufEnumStatement, ProtobufExtendStatement, ProtobufExtensionsStatement, ProtobufFieldStatement, ProtobufGroupFieldStatement, ProtobufMessageStatement, ProtobufOneofFieldStatement, ProtobufOptionStatement, ProtobufReservedStatement {

}

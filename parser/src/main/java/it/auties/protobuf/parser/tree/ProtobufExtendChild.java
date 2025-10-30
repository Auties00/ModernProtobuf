package it.auties.protobuf.parser.tree;

/**
 * Marker interface for statements that can appear inside a Protocol Buffer extend block.
 * <p>
 * This sealed interface restricts which statement types can be children of a
 * {@link ProtobufExtendStatement}. Extend blocks allow adding fields to existing messages
 * defined elsewhere. Permitted statements include:
 * </p>
 * <ul>
 *   <li>{@link ProtobufFieldStatement} - Extension field declarations</li>
 *   <li>{@link ProtobufGroupFieldStatement} - Extension group fields (deprecated)</li>
 *   <li>{@link ProtobufOneofFieldStatement} - Oneof blocks within extensions</li>
 *   <li>{@link ProtobufMessageStatement} - Nested message definitions</li>
 *   <li>{@link ProtobufEnumStatement} - Nested enum definitions</li>
 *   <li>{@link ProtobufExtendStatement} - Nested extend blocks</li>
 *   <li>{@link ProtobufExtensionsStatement} - Extension range declarations</li>
 *   <li>{@link ProtobufReservedStatement} - Reserved field declarations</li>
 *   <li>{@link ProtobufOptionStatement} - Options</li>
 *   <li>{@link ProtobufEmptyStatement} - Empty statements</li>
 * </ul>
 *
 * @see ProtobufExtendStatement
 */
public sealed interface ProtobufExtendChild
        extends ProtobufStatement
        permits ProtobufEmptyStatement, ProtobufEnumStatement, ProtobufExtendStatement, ProtobufExtensionsStatement, ProtobufFieldStatement, ProtobufGroupFieldStatement, ProtobufMessageStatement, ProtobufOneofFieldStatement, ProtobufOptionStatement, ProtobufReservedStatement {

}

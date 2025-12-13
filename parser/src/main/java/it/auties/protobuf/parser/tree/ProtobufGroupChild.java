package it.auties.protobuf.parser.tree;

/**
 * Marker interface for statements that can appear inside a Protocol Buffer group field.
 * <p>
 * This sealed interface restricts which statement types can be children of a
 * {@link ProtobufGroupStatement}. Groups are a deprecated feature from Protocol Buffers 2
 * that combine a message definition with a field declaration. Permitted statements include:
 * </p>
 * <ul>
 *   <li>{@link ProtobufFieldStatement} - Field declarations</li>
 *   <li>{@link ProtobufGroupStatement} - Nested group fields</li>
 *   <li>{@link ProtobufOneofStatement} - Oneof blocks</li>
 *   <li>{@link ProtobufMessageStatement} - Nested message definitions</li>
 *   <li>{@link ProtobufEnumStatement} - Nested enum definitions</li>
 *   <li>{@link ProtobufExtendStatement} - Extend blocks</li>
 *   <li>{@link ProtobufExtensionsStatement} - Extension range declarations</li>
 *   <li>{@link ProtobufReservedStatement} - Reserved field declarations</li>
 *   <li>{@link ProtobufEmptyStatement} - Empty statements</li>
 * </ul>
 * <p>
 * <strong>Note:</strong> Groups are deprecated and should not be used in new Protocol Buffer definitions.
 * </p>
 *
 * @see ProtobufGroupStatement
 */
public sealed interface ProtobufGroupChild
        extends ProtobufStatement
        permits ProtobufEmptyStatement, ProtobufEnumStatement, ProtobufExtendStatement, ProtobufExtensionsStatement, ProtobufFieldStatement, ProtobufGroupStatement, ProtobufMessageStatement, ProtobufOneofStatement, ProtobufReservedStatement {

}

package it.auties.protobuf.parser.tree;

/**
 * Marker interface for statements that can appear inside a Protocol Buffer service definition.
 * <p>
 * This sealed interface restricts which statement types can be children of a
 * {@link ProtobufServiceStatement}. Service-level statements include:
 * </p>
 * <ul>
 *   <li>{@link ProtobufMethodStatement} - RPC method declarations</li>
 *   <li>{@link ProtobufOptionStatement} - Service-level options</li>
 *   <li>{@link ProtobufEmptyStatement} - Empty statements</li>
 * </ul>
 *
 * @see ProtobufServiceStatement
 */
public sealed interface ProtobufServiceChild
        extends ProtobufStatement
        permits ProtobufOptionStatement, ProtobufMethodStatement, ProtobufEmptyStatement {

}

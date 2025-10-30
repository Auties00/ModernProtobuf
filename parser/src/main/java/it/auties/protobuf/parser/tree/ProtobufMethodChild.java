package it.auties.protobuf.parser.tree;

/**
 * Marker interface for statements that can appear inside a Protocol Buffer RPC method definition.
 * <p>
 * This sealed interface restricts which statement types can be children of a
 * {@link ProtobufMethodStatement}. RPC methods can only contain options and empty statements.
 * Permitted statements include:
 * </p>
 * <ul>
 *   <li>{@link ProtobufOptionStatement} - Method-level options</li>
 *   <li>{@link ProtobufEmptyStatement} - Empty statements</li>
 * </ul>
 *
 * @see ProtobufMethodStatement
 */
public sealed interface ProtobufMethodChild
        extends ProtobufStatement
        permits ProtobufEmptyStatement, ProtobufOptionStatement {

}

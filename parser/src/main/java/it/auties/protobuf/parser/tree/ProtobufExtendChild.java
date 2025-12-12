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
 * </ul>
 *
 * @see ProtobufExtendStatement
 */
public sealed interface ProtobufExtendChild
        extends ProtobufStatement
        permits ProtobufFieldStatement, ProtobufGroupFieldStatement {

}

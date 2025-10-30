package it.auties.protobuf.parser.tree;

/**
 * Marker interface for statements that can appear at the top level of a Protocol Buffer document.
 * <p>
 * This sealed interface restricts which statement types can be direct children of a
 * {@link ProtobufDocumentTree}. Top-level statements include:
 * </p>
 * <ul>
 *   <li>{@link ProtobufSyntaxStatement} - Syntax version declaration</li>
 *   <li>{@link ProtobufPackageStatement} - Package declaration</li>
 *   <li>{@link ProtobufImportStatement} - Import statements</li>
 *   <li>{@link ProtobufOptionStatement} - File-level options</li>
 *   <li>{@link ProtobufMessageStatement} - Message definitions</li>
 *   <li>{@link ProtobufEnumStatement} - Enum definitions</li>
 *   <li>{@link ProtobufServiceStatement} - Service definitions</li>
 *   <li>{@link ProtobufExtendStatement} - Extend blocks</li>
 *   <li>{@link ProtobufEmptyStatement} - Empty statements</li>
 * </ul>
 *
 * @see ProtobufDocumentTree
 */
public sealed interface ProtobufDocumentChild
        extends ProtobufStatement
        permits ProtobufEmptyStatement, ProtobufEnumStatement, ProtobufExtendStatement, ProtobufImportStatement, ProtobufMessageStatement, ProtobufOptionStatement, ProtobufPackageStatement, ProtobufServiceStatement, ProtobufSyntaxStatement {

}

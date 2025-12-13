package it.auties.protobuf.parser.tree;

/**
 * Abstract base class for all Protocol Buffer statement implementations in the AST.
 * <p>
 * This sealed class provides common functionality for all statement types, including
 * line number tracking and parent-child relationships. It exists to provide package-private
 * mutation methods (particularly {@code setParent}) that cannot be exposed through the
 * public {@link ProtobufStatement} interface.
 * </p>
 * <p>
 * All concrete statement classes extend from this base class, which ensures consistent
 * implementation of core AST node behaviors. The sealed nature of this class restricts
 * the permitted subclasses to those defined within the parser module.
 * </p>
 * <p>
 * Key responsibilities:
 * </p>
 * <ul>
 *   <li>Tracking the statement's location in the source file (line number)</li>
 *   <li>Managing the parent-child relationship within the AST</li>
 *   <li>Providing package-private mutation methods for AST construction</li>
 *   <li>Implementing common {@link ProtobufTree} behaviors</li>
 * </ul>
 * <p>
 * This class is part of the internal implementation and is not exposed in the public API.
 * Users of the parser should interact with statements through the {@link ProtobufStatement}
 * interface and its specific subtypes.
 * </p>
 *
 * @see ProtobufStatement
 * @see ProtobufStatementWithBodyImpl
 * @see ProtobufTree
 */
sealed abstract class ProtobufStatementImpl
        implements ProtobufStatement
        permits ProtobufEmptyStatement, ProtobufEnumConstantStatement, ProtobufExtensionsStatement, ProtobufFieldStatement, ProtobufGroupStatement, ProtobufImportStatement, ProtobufOneofStatement, ProtobufOptionStatement, ProtobufPackageStatement, ProtobufReservedStatement, ProtobufStatementWithBodyImpl, ProtobufSyntaxStatement {
    final int line;
    ProtobufTree.WithBody<?> parent;

    /**
     * Constructs a new statement implementation at the specified line number.
     *
     * @param line the line number in the source file where this statement appears
     */
    ProtobufStatementImpl(int line) {
        this.line = line;
    }

    /**
     * Returns the line number in the source file where this statement appears.
     *
     * @return the line number (1-indexed)
     */
    @Override
    public int line() {
        return line;
    }

    /**
     * Returns the parent node in the AST.
     * <p>
     * All statements have a parent that contains them, except for the root document.
     * The parent is always a node with a body (container node).
     * </p>
     *
     * @return the parent node, or null if this is a root node
     */
    @Override
    public ProtobufTree.WithBody<?> parent() {
        return parent;
    }

    /**
     * Checks whether this statement has a parent node.
     *
     * @return true if a parent is present, false otherwise
     */
    @Override
    public boolean hasParent() {
        return parent != null;
    }

    /**
     * Sets the parent node for this statement.
     * <p>
     * This method is package-private to prevent external code from breaking AST invariants.
     * It is called automatically when statements are added to or removed from container nodes.
     * </p>
     *
     * @param parent the parent node to set, or null to clear the parent
     */
    void setParent(ProtobufTree.WithBody<?> parent) {
        this.parent = parent;
    }
}

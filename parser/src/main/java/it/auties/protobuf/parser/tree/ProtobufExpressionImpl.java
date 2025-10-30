package it.auties.protobuf.parser.tree;

/**
 * Abstract base class for all Protocol Buffer expression implementations in the AST.
 * <p>
 * This sealed class provides common functionality for all expression types, including
 * line number tracking and parent-child relationships. It exists to provide package-private
 * mutation methods (particularly {@code setParent}) that cannot be exposed through the
 * public {@link ProtobufExpression} interface.
 * </p>
 * <p>
 * Expressions represent values, literals, and value-producing constructs within Protocol Buffer
 * declarations. Unlike statements which represent declarations and structural elements, expressions
 * represent data values used in options, field defaults, enum values, and other contexts.
 * </p>
 * <p>
 * Key responsibilities:
 * </p>
 * <ul>
 *   <li>Tracking the expression's location in the source file (line number)</li>
 *   <li>Managing the parent-child relationship within the AST</li>
 *   <li>Providing package-private mutation methods for AST construction</li>
 *   <li>Implementing common {@link ProtobufTree} behaviors</li>
 * </ul>
 * <p>
 * Permitted expression types include:
 * </p>
 * <ul>
 *   <li>Literals: boolean, numeric, string, null</li>
 *   <li>References: enum constants</li>
 *   <li>Composite: message values, options, ranges</li>
 * </ul>
 * <p>
 * This class is part of the internal implementation and is not exposed in the public API.
 * Users of the parser should interact with expressions through the {@link ProtobufExpression}
 * interface and its specific subtypes.
 * </p>
 *
 * @see ProtobufExpression
 * @see ProtobufStatementImpl
 * @see ProtobufTree
 */
sealed abstract class ProtobufExpressionImpl
        implements ProtobufExpression
        permits ProtobufBoolExpression, ProtobufEnumConstantExpression, ProtobufLiteralExpression, ProtobufMessageValueExpression, ProtobufNullExpression, ProtobufNumberExpression, ProtobufOptionExpression, ProtobufIntegerRangeExpression {
    final int line;
    ProtobufTree parent;

    /**
     * Constructs a new expression implementation at the specified line number.
     *
     * @param line the line number in the source file where this expression appears
     */
    ProtobufExpressionImpl(int line) {
        this.line = line;
    }

    /**
     * Returns the line number in the source file where this expression appears.
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
     * Expressions are always contained within statements or other expressions.
     * The parent can be any AST node type.
     * </p>
     *
     * @return the parent node, or null if not yet assigned
     */
    @Override
    public ProtobufTree parent() {
        return parent;
    }

    /**
     * Checks whether this expression has a parent node.
     *
     * @return true if a parent is present, false otherwise
     */
    @Override
    public boolean hasParent() {
        return parent != null;
    }

    /**
     * Sets the parent node for this expression.
     * <p>
     * This method is package-private to prevent external code from breaking AST invariants.
     * It is called automatically when expressions are added to or removed from parent nodes.
     * </p>
     *
     * @param parent the parent node to set, or null to clear the parent
     */
    void setParent(ProtobufTree parent) {
        this.parent = parent;
    }
}

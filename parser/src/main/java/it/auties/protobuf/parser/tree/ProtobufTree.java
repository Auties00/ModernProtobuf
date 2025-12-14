package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.expression.ProtobufExpression;
import it.auties.protobuf.parser.expression.ProtobufOptionExpression;
import it.auties.protobuf.parser.number.ProtobufInteger;
import it.auties.protobuf.parser.typeReference.ProtobufTypeReference;

import java.util.Optional;
import java.util.SequencedCollection;
import java.util.stream.Stream;

/**
 * Base interface for all nodes in the Protocol Buffer abstract syntax tree (AST).
 * <p>
 * This sealed interface represents the root of the AST hierarchy created by {@link it.auties.protobuf.parser.ProtobufParser}.
 * All nodes in the tree implement this interface, providing common functionality for navigation and metadata access.
 * The tree structure accurately represents the hierarchical nature of Protocol Buffer definitions, with documents
 * containing messages, messages containing fields, services containing methods, etc.
 * </p>
 * <p>
 * The interface hierarchy is organized as:
 * </p>
 * <ul>
 *   <li>{@link ProtobufDocumentTree} - Root node representing a .proto file</li>
 *   <li>{@link ProtobufStatement} - Declarations and statements (messages, enums, fields, etc.)</li>
 *   <li>{@link ProtobufExpression} - Values and expressions (literals, options, ranges, etc.)</li>
 * </ul>
 * <p>
 * The interface also provides several capability interfaces that nodes can implement:
 * </p>
 * <ul>
 *   <li>{@link WithName} - Nodes with identifiers (messages, fields, enums, services)</li>
 *   <li>{@link WithIndex} - Nodes with field numbers (fields)</li>
 *   <li>{@link WithBody} - Nodes that contain children (messages, enums, services, documents)</li>
 *   <li>{@link WithOptions} - Nodes that can have options (fields)</li>
 * </ul>
 *
 * @see ProtobufDocumentTree
 * @see ProtobufStatement
 * @see ProtobufExpression
 */
public sealed interface ProtobufTree
        permits ProtobufDocumentTree, ProtobufOptionDefinition, ProtobufStatement, ProtobufTree.WithBody, ProtobufTree.WithIndex, ProtobufTree.WithModifier, ProtobufTree.WithName, ProtobufTree.WithOptions, ProtobufTree.WithType {
    /**
     * Returns the line number in the source file where this tree node was parsed.
     *
     * @return the line number (starting from 1)
     */
    int line();

    /**
     * Indicates whether this tree node has been fully attributed during semantic analysis.
     * <p>
     * Attribution includes type resolution, import linking, and semantic validation.
     * Nodes are initially unattributed after parsing and become attributed during analysis.
     * </p>
     *
     * @return true if this node has been attributed, false otherwise
     */
    boolean isAttributed();

    /**
     * Returns the parent node of this tree node in the AST hierarchy.
     *
     * @return the parent node, or null if this is the root (document) node
     */
    ProtobufTree parent();

    /**
     * Checks whether this tree node has a parent.
     *
     * @return true if this node has a parent, false if it's the root node
     */
    boolean hasParent();

    /**
     * Capability interface for tree nodes that have a field number/index.
     * <p>
     * In Protocol Buffers, fields, enum constants, and other elements are identified by numeric indexes.
     * This interface provides access to these indexes.
     * </p>
     */
    sealed interface WithIndex
            extends ProtobufTree
            permits ProtobufEnumConstantStatement, ProtobufFieldStatement, ProtobufGroupStatement {
        /**
         * Returns the field number/index of this node.
         *
         * @return the index, or null if not yet set
         */
        ProtobufInteger index();

        /**
         * Checks whether this node has an index assigned.
         *
         * @return true if an index is present, false otherwise
         */
        boolean hasIndex();

        /**
         * Sets the field number/index for this node.
         *
         * @param index the index to set
         */
        void setIndex(ProtobufInteger index);
    }

    /**
     * Capability interface for tree nodes that have a name/identifier.
     * <p>
     * Named nodes include messages, enums, fields, services, methods, and other Protocol Buffer
     * declarations. This interface provides access to both the simple name and the fully qualified name.
     * </p>
     */
    sealed interface WithName
            extends ProtobufTree
            permits ProtobufEnumConstantStatement, ProtobufEnumStatement, ProtobufFieldStatement, ProtobufGroupStatement, ProtobufMessageStatement, ProtobufMethodStatement, ProtobufOneofStatement, ProtobufOptionDefinition, ProtobufServiceStatement {
        /**
         * Returns the simple (unqualified) name of this node.
         *
         * @return the name, or null if not yet set
         */
        String name();

        /**
         * Checks whether this node has a name assigned.
         *
         * @return true if a name is present, false otherwise
         */
        boolean hasName();

        /**
         * Sets the name for this node.
         *
         * @param name the name to set
         */
        void setName(String name);

        /**
         * Returns the fully qualified name of this node.
         * <p>
         * The qualified name includes the package name and any parent message names,
         * separated by dots. For example, a nested message might have a qualified name
         * like {@code com.example.OuterMessage.InnerMessage}.
         * </p>
         *
         * @return the fully qualified name, or null if the simple name is null
         */
        default String qualifiedName() {
            var name = name();
            if(name == null) {
                return null;
            }

            return switch (parent()) {
                case WithName parentWithName -> {
                    var qualifiedParentName = parentWithName.qualifiedName();
                    yield qualifiedParentName == null ? name : qualifiedParentName + "." + name;
                }
                case ProtobufDocumentTree document -> document.packageName()
                        .map(packageName -> packageName + "." + name)
                        .orElse(name);
                case null, default -> name;
            };
        }
    }

    /**
     * Capability interface for tree nodes that can have options.
     * <p>
     * Options are key-value pairs in square brackets that customize the behavior of Protocol Buffer
     * elements. For example: {@code [packed=true]} or {@code [deprecated=true]}.
     * </p>
     */
    sealed interface WithOptions
            extends ProtobufTree
            permits ProtobufEnumConstantStatement, ProtobufExtensionsStatement, ProtobufFieldStatement, ProtobufGroupStatement {
        /**
         * Returns all options attached to this node.
         *
         * @return a sequenced collection of option expressions
         */
        SequencedCollection<ProtobufOptionExpression> options();

        /**
         * Retrieves a specific option by name.
         *
         * @param name the option name to look up
         * @return an Optional containing the option if found, or empty otherwise
         */
        Optional<ProtobufOptionExpression> getOption(String name);

        /**
         * Adds an option to this node.
         *
         * @param value the option expression to add
         */
        void addOption(ProtobufOptionExpression value);

        /**
         * Removes an option by name.
         *
         * @param name the name of the option to remove
         * @return true if an option was removed, false otherwise
         */
        boolean removeOption(String name);
    }

    /**
     * Capability interface for tree nodes that contain child nodes.
     * <p>
     * Container nodes include documents (containing top-level declarations), messages (containing fields
     * and nested types), enums (containing constants), services (containing methods), etc.
     * This interface provides comprehensive methods for accessing and manipulating children.
     * </p>
     *
     * @param <T> the type of child statements this container holds
     */
    sealed interface WithBody<T extends ProtobufStatement>
            extends ProtobufTree
            permits ProtobufDocumentTree, ProtobufEnumStatement, ProtobufExtendStatement, ProtobufGroupStatement, ProtobufMessageStatement, ProtobufMethodStatement, ProtobufOneofStatement, ProtobufServiceStatement, ProtobufStatementWithBodyImpl {
        /**
         * Returns all direct children of this node.
         *
         * @return a sequenced collection of child statements
         */
        SequencedCollection<T> children();

        /**
         * Adds a child statement to this container.
         *
         * @param statement the child statement to add
         */
        void addChild(T statement);

        /**
         * Removes a child statement from this container.
         *
         * @param statement the child statement to remove
         * @return true if the child was removed, false otherwise
         */
        boolean removeChild(T statement);

        /**
         * Finds the first direct child of the specified type.
         *
         * @param clazz the class of the child type to find
         * @param <V> the child type
         * @return an Optional containing the first matching child, or empty if none found
         */
        <V extends ProtobufTree> Optional<? extends V> getDirectChildByType(Class<V> clazz);

        /**
         * Returns a stream of all direct children of the specified type.
         *
         * @param clazz the class of the child type to find
         * @param <V> the child type
         * @return a stream of matching children
         */
        <V extends ProtobufTree> Stream<? extends V> getDirectChildrenByType(Class<V> clazz);

        /**
         * Finds a direct child by name.
         *
         * @param name the name to search for
         * @return an Optional containing the matching child, or empty if none found
         */
        Optional<? extends WithName> getDirectChildByName(String name);

        /**
         * Finds a direct child by index/field number.
         *
         * @param index the index to search for
         * @return an Optional containing the matching child, or empty if none found
         */
        Optional<? extends WithIndex> getDirectChildByIndex(long index);

        /**
         * Finds a direct child by both name and type.
         *
         * @param name the name to search for
         * @param clazz the class of the child type to find
         * @param <V> the child type
         * @return an Optional containing the matching child, or empty if none found
         */
        <V extends ProtobufTree> Optional<? extends V> getDirectChildByNameAndType(String name, Class<V> clazz);

        /**
         * Finds a direct child by both index and type.
         *
         * @param index the index to search for
         * @param clazz the class of the child type to find
         * @param <V> the child type
         * @return an Optional containing the matching child, or empty if none found
         */
        <V extends ProtobufTree> Optional<? extends V> getDirectChildByIndexAndType(long index, Class<V> clazz);

        /**
         * Finds the first descendant (direct or nested) of the specified type.
         *
         * @param clazz the class of the descendant type to find
         * @param <V> the descendant type
         * @return an Optional containing the first matching descendant, or empty if none found
         */
        <V extends ProtobufTree> Optional<? extends V> getAnyChildByType(Class<V> clazz);

        /**
         * Finds a descendant by both name and type.
         *
         * @param name the name to search for
         * @param clazz the class of the descendant type to find
         * @param <V> the descendant type
         * @return an Optional containing the matching descendant, or empty if none found
         */
        <V extends ProtobufTree.WithName> Optional<? extends V> getAnyChildByNameAndType(String name, Class<V> clazz);

        /**
         * Returns a stream of all descendants (direct or nested) of the specified type.
         *
         * @param clazz the class of the descendant type to find
         * @param <V> the descendant type
         * @return a stream of matching descendants
         */
        <V extends ProtobufTree> Stream<? extends V> getAnyChildrenByType(Class<V> clazz);

        /**
         * Returns a stream of all descendants matching both name and type.
         *
         * @param name the name to search for
         * @param clazz the class of the descendant type to find
         * @param <V> the descendant type
         * @return a stream of matching descendants
         */
        <V extends ProtobufTree.WithName> Stream<? extends V> getAnyChildrenByNameAndType(String name, Class<V> clazz);
    }

    sealed interface WithType
            extends ProtobufTree
            permits ProtobufFieldStatement, ProtobufGroupStatement, ProtobufOptionDefinition {
        ProtobufTypeReference type();
        boolean hasType();
        void setType(ProtobufTypeReference type);
    }

    sealed interface WithModifier
            extends ProtobufTree
            permits ProtobufFieldStatement, ProtobufGroupStatement {
        ProtobufModifier modifier();
        boolean hasModifier();
        void setModifier(ProtobufModifier modifier);
    }
}

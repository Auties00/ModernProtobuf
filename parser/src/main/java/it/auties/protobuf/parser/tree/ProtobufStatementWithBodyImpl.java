package it.auties.protobuf.parser.tree;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Stream;

/**
 * Abstract base class for Protocol Buffer statements that contain child statements (have a body).
 * <p>
 * This sealed class extends {@link ProtobufStatementImpl} to provide container functionality for
 * statements that have a body of child statements. Examples include messages, enums, services,
 * extend blocks, and RPC methods.
 * </p>
 * <p>
 * This class implements {@link ProtobufTree.WithBody}, providing a complete suite of child
 * management and query methods. It maintains a list of child statements and provides both
 * direct child queries (immediate children only) and recursive queries (descendants at any depth).
 * </p>
 * <p>
 * Key responsibilities:
 * </p>
 * <ul>
 *   <li>Managing a collection of child statements</li>
 *   <li>Maintaining parent-child relationships bidirectionally</li>
 *   <li>Providing type-safe child queries by class type</li>
 *   <li>Supporting queries by name and index for applicable child types</li>
 *   <li>Implementing recursive tree traversal for descendant queries</li>
 * </ul>
 * <p>
 * Permitted container statement types:
 * </p>
 * <ul>
 *   <li>{@link ProtobufMessageStatement}: Contains fields, nested types, options, etc.</li>
 *   <li>{@link ProtobufEnumStatement}: Contains enum constants and options</li>
 *   <li>{@link ProtobufServiceStatement}: Contains RPC method declarations</li>
 *   <li>{@link ProtobufMethodStatement}: Contains method options</li>
 *   <li>{@link ProtobufExtendStatement}: Contains extension fields</li>
 * </ul>
 * <p>
 * The class provides both instance methods that operate on this statement's children and
 * static utility methods that can be reused by other classes (like {@link ProtobufDocumentTree})
 * for child queries.
 * </p>
 *
 * @param <CHILD> the type of child statements this container can hold
 * @see ProtobufStatementImpl
 * @see ProtobufTree.WithBody
 * @see ProtobufDocumentTree
 */
sealed class ProtobufStatementWithBodyImpl<CHILD extends ProtobufStatement>
        extends ProtobufStatementImpl
        implements ProtobufTree.WithBody<CHILD>
        permits ProtobufEnumStatement, ProtobufExtendStatement, ProtobufMessageStatement, ProtobufMethodStatement, ProtobufServiceStatement {
    final LinkedList<CHILD> children;

    /**
     * Constructs a new statement with body at the specified line number.
     *
     * @param line the line number in the source file where this statement appears
     */
    public ProtobufStatementWithBodyImpl(int line) {
        super(line);
        this.children = new LinkedList<>();
    }

    /**
     * Returns an unmodifiable view of the child statements.
     *
     * @return an unmodifiable collection of children in insertion order
     */
    @Override
    public SequencedCollection<CHILD> children() {
        return Collections.unmodifiableSequencedCollection(children);
    }

    /**
     * Adds a child statement to this container.
     * <p>
     * The child is automatically linked to this statement as its parent.
     * </p>
     *
     * @param statement the child statement to add
     */
    @Override
    public void addChild(CHILD statement){
        children.add(statement);
        if(statement instanceof ProtobufStatementImpl mutableTree) {
            mutableTree.setParent(this);
        }
    }

    /**
     * Removes a child statement from this container.
     * <p>
     * The child's parent link is automatically cleared.
     * </p>
     *
     * @param statement the child statement to remove
     * @return true if the child was found and removed, false otherwise
     */
    @Override
    public boolean removeChild(CHILD statement){
        var result = children.remove(statement);
        if(result && statement instanceof ProtobufStatementImpl mutableTree) {
            mutableTree.setParent(null);
        }
        return result;
    }

    @Override
    public <V extends ProtobufTree> Optional<? extends V> getDirectChildByType(Class<V> clazz) {
        return getDirectChildrenByType(children, clazz)
                .findFirst();
    }

    @Override
    public <V extends ProtobufTree> Stream<? extends V> getDirectChildrenByType(Class<V> clazz) {
        return getDirectChildrenByType(children, clazz);
    }

    /**
     * Static utility method to find direct children of a specific type.
     * <p>
     * This method is reusable by other classes that manage child collections.
     * </p>
     *
     * @param children the collection of children to search
     * @param clazz    the type of children to find
     * @param <V>      the type parameter
     * @return a stream of matching children
     */
    static <V extends ProtobufTree> Stream<? extends V> getDirectChildrenByType(Collection<? extends ProtobufTree> children, Class<V> clazz) {
        if (clazz == null) {
            return Stream.empty();
        }

        return children.stream()
                .filter(entry -> clazz.isAssignableFrom(entry.getClass()))
                .map(clazz::cast);
    }

    @Override
    public Optional<? extends ProtobufTree.WithName> getDirectChildByName(String name){
        return getDirectChildByName(children, name);
    }

    /**
     * Static utility method to find a direct child by name.
     * <p>
     * This method is reusable by other classes that manage child collections.
     * </p>
     *
     * @param children the collection of children to search
     * @param name     the name to search for
     * @return optional containing the matching child, or empty if not found
     */
    static Optional<WithName> getDirectChildByName(Collection<? extends ProtobufTree> children, String name) {
        if (name == null) {
            return Optional.empty();
        }

        return children.stream()
                .filter(entry -> entry instanceof WithName withName
                        && Objects.equals(withName.name(), name))
                .findFirst()
                .map(entry -> (WithName) entry);
    }

    @Override
    public Optional<? extends ProtobufTree.WithIndex> getDirectChildByIndex(long index){
        return getDirectChildByIndex(children, index);
    }

    /**
     * Static utility method to find a direct child by index (field number).
     * <p>
     * This method is reusable by other classes that manage child collections.
     * </p>
     *
     * @param children the collection of children to search
     * @param index    the field number to search for
     * @return optional containing the matching child, or empty if not found
     */
    static Optional<WithIndex> getDirectChildByIndex(Collection<? extends ProtobufTree> children, long index) {
        var indexAsBigInteger = BigInteger.valueOf(index);
        return children.stream()
                .filter(entry -> hasIndex(entry, indexAsBigInteger))
                .findFirst()
                .map(entry -> (WithIndex) entry);
    }

    /**
     * Helper method to check if a tree node has a specific index value.
     *
     * @param entry the tree node to check
     * @param index the index value to compare
     * @return true if the node has the specified index, false otherwise
     */
    private static boolean hasIndex(ProtobufTree entry, BigInteger index) {
        return entry instanceof WithIndex withIndex
               && withIndex.hasIndex()
               && withIndex.index().value().compareTo(index) == 0;
    }

    @Override
    public <V extends ProtobufTree> Optional<? extends V> getDirectChildByNameAndType(String name, Class<V> clazz){
        return getDirectChildByNameAndType(children, name, clazz);
    }

    /**
     * Static utility method to find a direct child by name and type.
     * <p>
     * This method is reusable by other classes that manage child collections.
     * </p>
     *
     * @param children the collection of children to search
     * @param name     the name to search for
     * @param clazz    the type of child to find
     * @param <V>      the type parameter
     * @return optional containing the matching child, or empty if not found
     */
    static  <V extends ProtobufTree> Optional<V> getDirectChildByNameAndType(Collection<? extends ProtobufTree> children, String name, Class<V> clazz) {
        if (name == null) {
            return Optional.empty();
        }

        return children.stream()
                .filter(entry -> clazz.isAssignableFrom(entry.getClass())
                        && entry instanceof WithName withName
                        && Objects.equals(withName.name(), name))
                .findFirst()
                .map(clazz::cast);
    }

    @Override
    public <V extends ProtobufTree> Optional<? extends V> getDirectChildByIndexAndType(long index, Class<V> clazz) {
        return getDirectChildByIndexAndType(children, index, clazz);
    }

    @Override
    public <V extends ProtobufTree> Optional<? extends V> getAnyChildByType(Class<V> clazz) {
        return getAnyChildrenByType(clazz)
                .findFirst();
    }

    @Override
    public <V extends WithName> Optional<? extends V> getAnyChildByNameAndType(String name, Class<V> clazz) {
        return ProtobufStatementWithBodyImpl.getAnyChildrenByNameAndType(children, name, clazz)
                .findFirst();
    }

    /**
     * Static utility method to find a direct child by index and type.
     * <p>
     * This method is reusable by other classes that manage child collections.
     * </p>
     *
     * @param children the collection of children to search
     * @param index    the field number to search for
     * @param clazz    the type of child to find
     * @param <V>      the type parameter
     * @return optional containing the matching child, or empty if not found
     */
    static <V extends ProtobufTree> Optional<V> getDirectChildByIndexAndType(Collection<? extends ProtobufTree> children, long index, Class<V> clazz) {
        var indexAsBigInteger = BigInteger.valueOf(index);
        return children.stream()
                .filter(entry -> clazz.isAssignableFrom(entry.getClass()) && hasIndex(entry, indexAsBigInteger))
                .findFirst()
                .map(clazz::cast);
    }

    @Override
    public <V extends ProtobufTree> Stream<? extends V> getAnyChildrenByType(Class<V> clazz) {
        return getAnyChildrenByType(children, clazz);
    }

    /**
     * Static utility method to recursively find all descendants of a specific type.
     * <p>
     * This method performs a breadth-first traversal of the AST starting from the given
     * children, visiting all descendants at any depth. This is reusable by other classes
     * that manage child collections.
     * </p>
     *
     * @param children the collection of children to search from
     * @param clazz    the type of descendants to find
     * @param <V>      the type parameter
     * @return a stream of all matching descendants at any depth
     */
    static <V extends ProtobufTree> Stream<V> getAnyChildrenByType(Collection<? extends ProtobufTree> children, Class<V> clazz) {
        return children.stream().mapMulti((child, vConsumer) -> {
            var remaining = new LinkedList<ProtobufTree>();
            remaining.add(child);
            while (!remaining.isEmpty()) {
                var entry = remaining.removeFirst();
                if (entry instanceof WithBody<?> withBody) {
                    remaining.addAll(withBody.children());
                }

                if (clazz.isAssignableFrom(entry.getClass())) {
                    vConsumer.accept(clazz.cast(entry));
                }
            }
        });
    }

    @Override
    public <V extends WithName> Stream<? extends V> getAnyChildrenByNameAndType(String name, Class<V> clazz) {
        return getAnyChildrenByNameAndType(children, name, clazz);
    }

    /**
     * Static utility method to recursively find all descendants with a specific name and type.
     * <p>
     * This method performs a breadth-first traversal of the AST starting from the given
     * children, visiting all descendants at any depth. This is reusable by other classes
     * that manage child collections.
     * </p>
     *
     * @param children the collection of children to search from
     * @param name     the name to search for
     * @param clazz    the type of descendants to find
     * @param <V>      the type parameter (must extend WithName)
     * @return a stream of all matching descendants at any depth
     */
    static <V extends WithName> Stream<V> getAnyChildrenByNameAndType(Collection<? extends ProtobufTree> children,String name, Class<V> clazz) {
        return children.stream().mapMulti((child, vConsumer) -> {
            var remaining = new LinkedList<ProtobufTree>();
            remaining.add(child);
            while (!remaining.isEmpty()) {
                var entry = remaining.removeFirst();
                if (entry instanceof WithBody<?> withBody) {
                    remaining.addAll(withBody.children());
                }

                if (entry instanceof WithName withName
                        && Objects.equals(withName.name(), name)
                        && clazz.isAssignableFrom(entry.getClass())) {
                    vConsumer.accept(clazz.cast(entry));
                }
            }
        });
    }

    /**
     * Checks whether this statement is fully attributed.
     * <p>
     * A statement with a body is attributed when at least one of its children is attributed.
     * </p>
     *
     * @return true if any child is attributed, false otherwise
     */
    @Override
    public boolean isAttributed() {
        return children.stream().anyMatch(ProtobufTree::isAttributed);
    }
}

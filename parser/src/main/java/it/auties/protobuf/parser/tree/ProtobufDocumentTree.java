package it.auties.protobuf.parser.tree;

import it.auties.protobuf.model.ProtobufVersion;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Represents the root node of a Protocol Buffer document AST.
 * <p>
 * The document tree is the top-level container for all statements in a .proto file. It holds
 * file-level declarations such as syntax, package, imports, options, and type definitions
 * (messages, enums, services). The document also tracks the file's location on disk.
 * </p>
 * <h2>Structure of a .proto file:</h2>
 * <pre>{@code
 * // example.proto
 * syntax = "proto3";
 *
 * package com.example;
 *
 * import "google/protobuf/timestamp.proto";
 * import public "other.proto";
 *
 * option java_package = "com.example.proto";
 * option java_outer_classname = "ExampleProtos";
 *
 * message Person {
 *   string name = 1;
 *   int32 age = 2;
 * }
 *
 * enum Status {
 *   UNKNOWN = 0;
 *   ACTIVE = 1;
 * }
 *
 * service UserService {
 *   rpc GetUser(Person) returns (Person);
 * }
 * }</pre>
 * <p>
 * The document tree can contain these child elements:
 * </p>
 * <ul>
 *   <li><strong>Syntax statement:</strong> Declares proto2 or proto3 (at most one)</li>
 *   <li><strong>Package statement:</strong> Declares the package name (at most one)</li>
 *   <li><strong>Import statements:</strong> Imports other .proto files</li>
 *   <li><strong>Option statements:</strong> File-level configuration options</li>
 *   <li><strong>Message statements:</strong> Message type definitions</li>
 *   <li><strong>Enum statements:</strong> Enum type definitions</li>
 *   <li><strong>Service statements:</strong> RPC service definitions</li>
 *   <li><strong>Extend statements:</strong> Extensions to existing messages</li>
 *   <li><strong>Empty statements:</strong> Standalone semicolons</li>
 * </ul>
 * <p>
 * This class is the root of the AST and has no parent. It provides convenience methods to
 * access the syntax version and package name directly. The document is attributed when all
 * of its child statements are attributed.
 * </p>
 * <p>
 * The {@code qualifiedPath()} method returns the document's path in the Protocol Buffer
 * namespace, combining the package name and file name (e.g., "com/example/example.proto").
 * </p>
 *
 * @see ProtobufDocumentChild
 * @see ProtobufSyntaxStatement
 * @see ProtobufPackageStatement
 * @see ProtobufImportStatement
 */
public final class ProtobufDocumentTree
        implements ProtobufTree, ProtobufTree.WithBody<ProtobufDocumentChild> {
    private final Path location;
    private final List<ProtobufDocumentChild> children;

    /**
     * Constructs a new document tree for a file at the specified location.
     *
     * @param location the file system path to the .proto file, or null if unknown
     */
    public ProtobufDocumentTree(Path location) {
        this.location = location;
        this.children = new ArrayList<>();
    }

    /**
     * Constructs a new document tree with no associated file location.
     * <p>
     * This constructor is useful for programmatically created ASTs or testing.
     * </p>
     */
    public ProtobufDocumentTree() {
        this(null);
    }

    /**
     * Returns the line number in the source file.
     * <p>
     * Documents are always at line 0 as they represent the entire file.
     * </p>
     *
     * @return always returns 0
     */
    @Override
    public int line() {
        return 0;
    }

    /**
     * Checks whether this document is fully attributed.
     * <p>
     * A document is attributed when all of its child statements are attributed.
     * </p>
     *
     * @return true if all children are attributed, false otherwise
     */
    @Override
    public boolean isAttributed() {
        return children.stream()
                .allMatch(ProtobufTree::isAttributed);
    }

    /**
     * Returns the file system location of this .proto file.
     *
     * @return optional containing the file path, or empty if no location is set
     */
    public Optional<Path> location() {
        return Optional.ofNullable(location);
    }

    /**
     * Returns the parent tree node.
     * <p>
     * Documents are root nodes and have no parent.
     * </p>
     *
     * @return always returns null
     */
    @Override
    public ProtobufTree parent() {
        return null;
    }

    /**
     * Checks whether this document has a parent.
     * <p>
     * Documents are root nodes and never have parents.
     * </p>
     *
     * @return always returns false
     */
    @Override
    public boolean hasParent() {
        return false;
    }

    /**
     * Returns the fully qualified path for this document in the Protocol Buffer namespace.
     * <p>
     * Combines the package name (with dots converted to slashes) and the file name.
     * For example, package "com.example" with file "foo.proto" becomes "com/example/foo.proto".
     * </p>
     *
     * @return the qualified path string
     */
    public String qualifiedPath() {
        var result = new StringBuilder();
        packageName().ifPresent(value -> {
            result.append(value.replaceAll("\\.", "/"));
        });
        location().ifPresent(value -> {
            if(!result.isEmpty()) {
                result.append('/');
            }
            result.append(value.getFileName());
        });
        return result.toString();
    }

    /**
     * Returns the Protocol Buffer syntax version declared in this document.
     * <p>
     * Extracts the version from the syntax statement if present. If no syntax statement
     * exists, proto2 is assumed by default according to Protocol Buffer specifications.
     * </p>
     *
     * @return optional containing the syntax version, or empty if no syntax statement exists
     */
    public Optional<ProtobufVersion> syntax() {
        return getDirectChildByType(ProtobufSyntaxStatement.class)
                .map(ProtobufSyntaxStatement::version);
    }

    /**
     * Returns the package name declared in this document.
     * <p>
     * Extracts the package name from the package statement if present.
     * </p>
     *
     * @return optional containing the package name, or empty if no package statement exists
     */
    public Optional<String> packageName() {
        return getDirectChildByType(ProtobufPackageStatement.class)
                .map(ProtobufPackageStatement::name);
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();
        children().forEach(statement -> {
            builder.append(statement);
            builder.append("\n");
        });
        return builder.toString();
    }

    @Override
    public SequencedCollection<ProtobufDocumentChild> children() {
        return Collections.unmodifiableList(children);
    }

    @Override
    public void addChild(ProtobufDocumentChild statement) {
        children.add(statement);
        if(statement instanceof ProtobufStatementImpl mutableTree) {
            mutableTree.setParent(this);
        }
    }

    @Override
    public boolean removeChild(ProtobufDocumentChild statement) {
        var result = children.remove(statement);
        if(result && statement instanceof ProtobufStatementImpl mutableTree) {
            mutableTree.setParent(null);
        }
        return result;
    }

    @Override
    public <V extends ProtobufTree> Optional<? extends V> getDirectChildByType(Class<V> clazz) {
        return ProtobufStatementWithBodyImpl.getDirectChildrenByType(children, clazz)
                .findFirst();
    }

    @Override
    public <V extends ProtobufTree> Stream<? extends V> getDirectChildrenByType(Class<V> clazz) {
        return ProtobufStatementWithBodyImpl.getDirectChildrenByType(children, clazz);
    }

    @Override
    public Optional<? extends WithName> getDirectChildByName(String name) {
        return ProtobufStatementWithBodyImpl.getDirectChildByName(children, name);
    }

    /**
     * Returns a direct child with the specified index.
     * <p>
     * Documents do not have indexed children at the file level, so this always returns empty.
     * </p>
     *
     * @param index the index to search for
     * @return always returns empty
     */
    @Override
    public Optional<? extends WithIndex> getDirectChildByIndex(long index) {
        return Optional.empty(); // No direct child with an index exists in a document
    }

    @Override
    public <V extends ProtobufTree> Optional<? extends V> getDirectChildByNameAndType(String name, Class<V> clazz) {
        return ProtobufStatementWithBodyImpl.getDirectChildByNameAndType(children, name, clazz);
    }

    /**
     * Returns a direct child with the specified index and type.
     * <p>
     * Documents do not have indexed children at the file level, so this always returns empty.
     * </p>
     *
     * @param index the index to search for
     * @param clazz the type of child to search for
     * @param <V>   the type parameter
     * @return always returns empty
     */
    @Override
    public <V extends ProtobufTree> Optional<? extends V> getDirectChildByIndexAndType(long index, Class<V> clazz) {
        return Optional.empty(); // No direct child with an index exists in a document
    }

    @Override
    public <V extends ProtobufTree> Optional<? extends V> getAnyChildByType(Class<V> clazz) {
        return ProtobufStatementWithBodyImpl.getAnyChildrenByType(children, clazz)
                .findFirst();
    }

    @Override
    public <V extends WithName> Optional<? extends V> getAnyChildByNameAndType(String name, Class<V> clazz) {
        return ProtobufStatementWithBodyImpl.getAnyChildrenByNameAndType(children, name, clazz)
                .findFirst();
    }

    @Override
    public <V extends ProtobufTree> Stream<? extends V> getAnyChildrenByType(Class<V> clazz) {
        return ProtobufStatementWithBodyImpl.getAnyChildrenByType(children, clazz);
    }

    @Override
    public <V extends WithName> Stream<? extends V> getAnyChildrenByNameAndType(String name, Class<V> clazz) {
        return ProtobufStatementWithBodyImpl.getAnyChildrenByNameAndType(children, name, clazz);
    }
}

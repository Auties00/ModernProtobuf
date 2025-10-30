package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.type.ProtobufGroupTypeReference;
import it.auties.protobuf.parser.type.ProtobufTypeReference;

import java.util.*;
import java.util.stream.Stream;

/**
 * Represents a group field declaration in the Protocol Buffer AST.
 * <p>
 * Groups are a deprecated proto2 feature that combines a nested message type definition with
 * a field declaration. The group's name defines both the field name (lowercased) and the
 * message type name (capitalized). Groups were deprecated in favor of nested message types.
 * </p>
 * <h2>Examples:</h2>
 * <pre>{@code
 * message SearchResponse {
 *   // Deprecated group syntax
 *   repeated group Result = 1 {
 *     required string url = 2;
 *     optional string title = 3;
 *     repeated string snippets = 4;
 *   }
 * }
 *
 * // The above is equivalent to the modern nested message approach:
 * message SearchResponse {
 *   message Result {
 *     required string url = 2;
 *     optional string title = 3;
 *     repeated string snippets = 4;
 *   }
 *   repeated Result result = 1;
 * }
 * }</pre>
 * <p>
 * Group characteristics:
 * </p>
 * <ul>
 *   <li>Groups define both a message type and a field</li>
 *   <li>The group name must start with a capital letter (message type convention)</li>
 *   <li>The field name is the lowercased version of the group name</li>
 *   <li>Groups can have modifiers (optional, required, repeated)</li>
 *   <li>Groups have a field number like regular fields</li>
 *   <li>Groups use a different wire format encoding than nested messages</li>
 * </ul>
 * <p>
 * Groups can contain the same child elements as messages:
 * </p>
 * <ul>
 *   <li><strong>Fields:</strong> Data members of the group</li>
 *   <li><strong>Nested messages:</strong> Additional message definitions</li>
 *   <li><strong>Nested enums:</strong> Enum definitions</li>
 *   <li><strong>Options:</strong> Configuration options</li>
 *   <li><strong>Empty statements:</strong> Standalone semicolons</li>
 * </ul>
 * <p>
 * This class extends {@link ProtobufFieldStatement} and maintains its own body of child statements.
 * The type is automatically set to a {@link ProtobufGroupTypeReference} and cannot be changed.
 * </p>
 * <p>
 * <strong>Note:</strong> Groups are deprecated and should not be used in new .proto files.
 * Use nested message types instead.
 * </p>
 *
 * @see ProtobufGroupChild
 * @see ProtobufFieldStatement
 * @see ProtobufGroupTypeReference
 */
public final class ProtobufGroupFieldStatement
        extends ProtobufFieldStatement
        implements ProtobufTree.WithBody<ProtobufGroupChild>,
                   ProtobufMessageChild, ProtobufOneofChild, ProtobufGroupChild, ProtobufExtendChild {
    private final List<ProtobufGroupChild> children;

    /**
     * Constructs a new group field statement at the specified line number.
     *
     * @param line the line number in the source file
     */
    public ProtobufGroupFieldStatement(int line) {
        super(line);
        this.children = new ArrayList<>();
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();

        if(modifier != null && modifier != Modifier.NONE) {
            builder.append(modifier);
            builder.append(" ");
        }

        builder.append("group");
        builder.append(" ");

        var name = Objects.requireNonNullElse(this.name, "[missing]");
        builder.append(name);
        builder.append(" ");

        var index = Objects.requireNonNullElse(this.index, "[missing]");
        builder.append("=");
        builder.append(" ");
        builder.append(index);
        builder.append(" ");

        writeOptions(builder);

        builder.append("{");
        builder.append("\n");

        if(children.isEmpty()) {
            builder.append("\n");
        } else {
            children.forEach(statement -> {
                builder.append("    ");
                builder.append(statement);
                builder.append("\n");
            });
        }

        builder.append("}");

        return builder.toString();
    }

    /**
     * Returns the type reference for this group field.
     * <p>
     * Group fields have an implicit type that references the group itself as a message type.
     * </p>
     *
     * @return a {@link ProtobufGroupTypeReference} referencing this group
     */
    @Override
    public ProtobufTypeReference type() {
        return new ProtobufGroupTypeReference(this);
    }

    /**
     * Setting the type of a group field is not supported.
     * <p>
     * Groups have an implicit type that cannot be changed.
     * </p>
     *
     * @param type the type reference (ignored)
     * @throws UnsupportedOperationException always thrown
     */
    @Override
    public void setType(ProtobufTypeReference type) {
        throw new UnsupportedOperationException("Cannot set the type of a group field");
    }

    @Override
    public SequencedCollection<ProtobufGroupChild> children() {
        return Collections.unmodifiableList(children);
    }

    @Override
    public void addChild(ProtobufGroupChild statement) {
        children.add(statement);
        if(statement instanceof ProtobufStatementImpl mutableTree) {
            mutableTree.setParent(this);
        }
    }

    @Override
    public boolean removeChild(ProtobufGroupChild statement) {
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

    @Override
    public Optional<? extends WithIndex> getDirectChildByIndex(long index) {
        return ProtobufStatementWithBodyImpl.getDirectChildByIndex(children, index);
    }

    @Override
    public <V extends ProtobufTree> Optional<? extends V> getDirectChildByNameAndType(String name, Class<V> clazz) {
        return ProtobufStatementWithBodyImpl.getDirectChildByNameAndType(children, name, clazz);
    }

    @Override
    public <V extends ProtobufTree> Optional<? extends V> getDirectChildByIndexAndType(long index, Class<V> clazz) {
        return ProtobufStatementWithBodyImpl.getDirectChildByIndexAndType(children, index, clazz);
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

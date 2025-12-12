package it.auties.protobuf.parser.tree;

import java.util.*;
import java.util.stream.Stream;

/**
 * Represents a oneof declaration in the Protocol Buffer AST.
 * <p>
 * A oneof is a group of fields where at most one field can be set at any given time. Setting
 * any member of the oneof automatically clears all other members. Oneofs are useful for
 * representing variants or union types.
 * </p>
 * <h2>Examples:</h2>
 * <pre>{@code
 * message SampleMessage {
 *   oneof test_oneof {
 *     string name = 4;
 *     int32 age = 9;
 *   }
 * }
 *
 * message Result {
 *   oneof result {
 *     string success = 1;
 *     string error = 2;
 *   }
 * }
 *
 * message SearchQuery {
 *   oneof query_type {
 *     string text = 1;
 *     int32 id = 2;
 *     Message nested = 3;
 *   }
 * }
 * }</pre>
 * <p>
 * Oneof restrictions and requirements:
 * </p>
 * <ul>
 *   <li>Oneof fields cannot use the {@code required}, {@code optional}, or {@code repeated} modifiers</li>
 *   <li>Oneof fields cannot be maps</li>
 *   <li>Field numbers must still be unique within the containing message</li>
 *   <li>Only regular fields and groups can appear within a oneof (no nested messages/enums)</li>
 * </ul>
 * <p>
 * Oneofs can contain:
 * </p>
 * <ul>
 *   <li><strong>Fields:</strong> Regular field declarations (the oneof members)</li>
 *   <li><strong>Options:</strong> Configuration options for the oneof</li>
 *   <li><strong>Empty statements:</strong> Standalone semicolons</li>
 * </ul>
 * <p>
 * This class extends {@link ProtobufFieldStatement} but does not use the type, index, or modifier
 * fields from the parent. It maintains its own list of child statements representing the oneof members.
 * </p>
 *
 * @see ProtobufOneofChild
 * @see ProtobufFieldStatement
 * @see ProtobufMessageChild
 */
public final class ProtobufOneofFieldStatement
        extends ProtobufFieldStatement
        implements ProtobufStatement, ProtobufTree.WithName, ProtobufTree.WithBody<ProtobufOneofChild>, ProtobufTree.WithBodyAndName<ProtobufOneofChild>,
                   ProtobufMessageChild, ProtobufGroupChild {
    private String name;
    private final List<ProtobufOneofChild> children;

    /**
     * Constructs a new oneof statement at the specified line number.
     *
     * @param line the line number in the source file
     */
    public ProtobufOneofFieldStatement(int line) {
        super(line);
        this.children = new ArrayList<>();
    }

    /**
     * Returns the generated class name for this oneof.
     * <p>
     * Generates a class name by capitalizing the first letter of the oneof name
     * and appending "Seal" (e.g., "result" becomes "ResultSeal").
     * </p>
     *
     * @return the generated class name
     */
    public String className() {
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1) + "Seal";
    }

    @Override
    public Modifier modifier() {
        return Modifier.NONE;
    }

    @Override
    public void setModifier(Modifier modifier) {

    }

    /**
     * Returns the oneof name.
     *
     * @return the oneof name, or null if not yet set
     */
    @Override
    public String name() {
        return name;
    }

    /**
     * Checks whether this oneof has a name assigned.
     *
     * @return true if a name is present, false otherwise
     */
    @Override
    public boolean hasName() {
        return name != null;
    }

    /**
     * Sets the name for this oneof.
     * <p>
     * The name must be a valid Protocol Buffer identifier and unique within the parent scope.
     * </p>
     *
     * @param name the oneof name to set
     */
    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();

        builder.append("oneof");
        builder.append(" ");

        var name = Objects.requireNonNullElse(this.name, "[missing]");
        builder.append(name);
        builder.append(" ");

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

    @Override
    public SequencedCollection<ProtobufOneofChild> children() {
        return Collections.unmodifiableList(children);
    }

    @Override
    public void addChild(ProtobufOneofChild statement) {
        children.add(statement);
        if(statement instanceof ProtobufStatementImpl mutableTree) {
            mutableTree.setParent(this);
        }
    }

    @Override
    public boolean removeChild(ProtobufOneofChild statement) {
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

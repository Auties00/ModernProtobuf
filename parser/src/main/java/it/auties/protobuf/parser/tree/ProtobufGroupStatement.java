package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.expression.ProtobufOptionExpression;
import it.auties.protobuf.parser.typeReference.ProtobufGroupTypeReference;
import it.auties.protobuf.parser.number.ProtobufInteger;
import it.auties.protobuf.parser.typeReference.ProtobufTypeReference;

import java.util.*;
import java.util.stream.Collectors;
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
public final class ProtobufGroupStatement
        extends ProtobufStatementImpl
        implements ProtobufStatement, ProtobufTree.WithName, ProtobufTree.WithIndex, ProtobufTree.WithOptions, ProtobufTree.WithBody<ProtobufGroupChild>, ProtobufTree.WithType, ProtobufTree.WithModifier, ProtobufOptionDefinition,
                   ProtobufMessageChild, ProtobufOneofChild, ProtobufGroupChild, ProtobufExtendChild {
    private ProtobufModifier modifier;
    private ProtobufTypeReference type;
    private String name;
    private ProtobufInteger index;
    private final List<ProtobufGroupChild> children;
    private final ProtobufGroupTypeReference reference;
    private final SequencedMap<String, ProtobufOptionExpression> options;

    /**
     * Constructs a new group field statement at the specified line number.
     *
     * @param line the line number in the source file
     */
    public ProtobufGroupStatement(int line) {
        super(line);
        this.children = new ArrayList<>();
        this.reference = new ProtobufGroupTypeReference(this);
        this.options = new LinkedHashMap<>();
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();

        if(modifier != null) {
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

        var options = this.options.sequencedEntrySet();
        if (!options.isEmpty()) {
            builder.append(" ");
            builder.append("[");
            var optionsToString = options.stream()
                    .map(entry -> entry.getValue().toString())
                    .collect(Collectors.joining(", "));
            builder.append(optionsToString);
            builder.append("]");
        }

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
    public ProtobufModifier modifier() {
        return modifier;
    }

    @Override
    public boolean hasModifier() {
        return modifier != null;
    }

    @Override
    public void setModifier(ProtobufModifier modifier) {
        this.modifier = modifier;
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
        return reference;
    }

    @Override
    public boolean hasType() {
        return true;
    }

    /**
     * Setting the type of a group field is not supported.
     * <p>
     * Groups have an implicit type that cannot be changed.
     * </p>
     *
     * @param type the type reference (ignored)
     */
    @Override
    public void setType(ProtobufTypeReference type) {

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

    @Override
    public ProtobufInteger index() {
        return index;
    }

    @Override
    public boolean hasIndex() {
        return index != null;
    }

    @Override
    public void setIndex(ProtobufInteger index) {
        this.index = index;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean hasName() {
        return name != null;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public SequencedCollection<ProtobufOptionExpression> options() {
        return Collections.unmodifiableSequencedCollection(options.sequencedValues());
    }

    @Override
    public Optional<ProtobufOptionExpression> getOption(String name) {
        return Optional.ofNullable(options.get(name));
    }

    @Override
    public void addOption(ProtobufOptionExpression value) {
        Objects.requireNonNull(value, "Cannot add null option");
        options.put(value.name().toString(), value);
    }

    @Override
    public boolean removeOption(String name) {
        return options.remove(name) != null;
    }

    @Override
    public boolean isAttributed() {
        return hasType() && hasName() && hasIndex();
    }
}

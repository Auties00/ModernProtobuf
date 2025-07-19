package it.auties.protobuf.parser.tree;

import java.util.*;
import java.util.stream.Stream;

public final class ProtobufOneofFieldStatement
        extends ProtobufFieldStatement
        implements ProtobufStatement, ProtobufTree.WithName, ProtobufTree.WithBody<ProtobufOneofChild>,
                   ProtobufMessageChild, ProtobufGroupChild {
    private String name;
    private final List<ProtobufOneofChild> children;

    public ProtobufOneofFieldStatement(int line) {
        super(line);
        this.children = new ArrayList<>();
    }

    public String className() {
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1) + "Seal";
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
        return ProtobufStatementWithBodyImpl.getDirectChildByType(children, clazz);
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
    public <V extends ProtobufTree> Stream<? extends V> getAnyChildrenByType(Class<V> clazz) {
        return ProtobufStatementWithBodyImpl.getAnyChildrenByType(children, clazz);
    }

    @Override
    public <V extends WithName> Stream<? extends V> getAnyChildrenByNameAndType(String name, Class<V> clazz) {
        return ProtobufStatementWithBodyImpl.getAnyChildrenByNameAndType(children, name, clazz);
    }
}

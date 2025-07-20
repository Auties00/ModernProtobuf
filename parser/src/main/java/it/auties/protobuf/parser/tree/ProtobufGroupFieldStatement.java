package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.type.ProtobufGroupTypeReference;
import it.auties.protobuf.parser.type.ProtobufTypeReference;

import java.util.*;
import java.util.stream.Stream;

public final class ProtobufGroupFieldStatement
        extends ProtobufFieldStatement
        implements ProtobufTree.WithBody<ProtobufGroupChild>,
                   ProtobufMessageChild, ProtobufOneofChild, ProtobufGroupChild {
    private final List<ProtobufGroupChild> children;
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

    @Override
    public ProtobufTypeReference type() {
        return new ProtobufGroupTypeReference(this);
    }

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
    public <V extends ProtobufTree> Stream<? extends V> getAnyChildrenByType(Class<V> clazz) {
        return ProtobufStatementWithBodyImpl.getAnyChildrenByType(children, clazz);
    }

    @Override
    public <V extends WithName> Stream<? extends V> getAnyChildrenByNameAndType(String name, Class<V> clazz) {
        return ProtobufStatementWithBodyImpl.getAnyChildrenByNameAndType(children, name, clazz);
    }
}

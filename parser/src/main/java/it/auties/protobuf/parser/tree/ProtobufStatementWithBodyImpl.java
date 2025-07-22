package it.auties.protobuf.parser.tree;

import java.util.*;
import java.util.stream.Stream;

sealed class ProtobufStatementWithBodyImpl<CHILD extends ProtobufStatement>
        extends ProtobufStatementImpl
        implements ProtobufTree.WithBody<CHILD>
        permits ProtobufEnumStatement, ProtobufExtendStatement, ProtobufMessageStatement, ProtobufMethodStatement, ProtobufServiceStatement {
    final LinkedList<CHILD> children;

    public ProtobufStatementWithBodyImpl(int line) {
        super(line);
        this.children = new LinkedList<>();
    }

    @Override
    public SequencedCollection<CHILD> children() {
        return Collections.unmodifiableSequencedCollection(children);
    }

    @Override
    public void addChild(CHILD statement){
        children.add(statement);
        if(statement instanceof ProtobufStatementImpl mutableTree) {
            mutableTree.setParent(this);
        }
    }

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

    static Optional<WithIndex> getDirectChildByIndex(Collection<? extends ProtobufTree> children, long index) {
        return children.stream()
                .filter(entry -> entry instanceof WithIndex withIndex
                        && withIndex.hasIndex()
                        && withIndex.index() == index)
                .findFirst()
                .map(entry -> (WithIndex) entry);
    }

    @Override
    public <V extends ProtobufTree> Optional<? extends V> getDirectChildByNameAndType(String name, Class<V> clazz){
        return getDirectChildByNameAndType(children, name, clazz);
    }

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

    static <V extends ProtobufTree> Optional<V> getDirectChildByIndexAndType(Collection<? extends ProtobufTree> children, long index, Class<V> clazz) {
        return children.stream()
                .filter(entry -> clazz.isAssignableFrom(entry.getClass())
                        && entry instanceof WithIndex withIndex
                        && withIndex.hasIndex()
                        && withIndex.index() == index)
                .findFirst()
                .map(clazz::cast);
    }

    @Override
    public <V extends ProtobufTree> Stream<? extends V> getAnyChildrenByType(Class<V> clazz) {
        return getAnyChildrenByType(children, clazz);
    }

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

    @Override
    public boolean isAttributed() {
        return children.stream().anyMatch(ProtobufTree::isAttributed);
    }
}

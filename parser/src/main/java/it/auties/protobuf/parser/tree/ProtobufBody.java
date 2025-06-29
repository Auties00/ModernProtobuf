package it.auties.protobuf.parser.tree;

import java.util.*;
import java.util.function.Consumer;

public final class ProtobufBody<CHILD extends ProtobufStatement> {
    private final int line;
    private final LinkedList<CHILD> children;
    private ProtobufTree.WithBody<?> owner;

    public ProtobufBody(int line) {
        this.line = line;
        this.children = new LinkedList<>();
    }

    public int line() {
        return line;
    }

    public ProtobufTree owner() {
        return owner;
    }

    public boolean hasOwner() {
        return owner != null;
    }

    void setOwner(ProtobufTree.WithBody<?> owner) {
        this.owner = owner;
    }

    public SequencedCollection<CHILD> children() {
        return Collections.unmodifiableSequencedCollection(children);
    }

    public void addChild(CHILD statement){
        if(owner == null) {
            throw new IllegalStateException("This body is not owned by any tree");
        }

        children.add(statement);
        if(statement instanceof ProtobufStatementImpl mutableTree) {
            mutableTree.setParent(owner);
        }
    }

    public boolean removeChild(CHILD statement){
        if(owner == null) {
            throw new IllegalStateException("This body is not owned by any tree");
        }

        if(statement.parent() != owner) {
            return false;
        }

        var result = children.remove(statement);
        if(result && statement instanceof ProtobufStatementImpl mutableTree) {
            mutableTree.setParent(null);
        }
        return result;
    }

    public Optional<? extends ProtobufTree.WithName> getDirectChildByName(String name){
        if (name == null) {
            return Optional.empty();
        }

        return children.stream()
                .filter(entry -> entry instanceof ProtobufTree.WithName)
                .map(entry -> (ProtobufTree.WithName) entry)
                .filter(entry -> Objects.equals(entry.name(), name))
                .findFirst();
    }

    public <V extends ProtobufTree> Optional<? extends V> getDirectChildByNameAndType(String name, Class<V> clazz){
        if (name == null) {
            return Optional.empty();
        }

        return children.stream()
                .filter(entry -> clazz.isAssignableFrom(entry.getClass()) && entry instanceof ProtobufTree.WithName withName && Objects.equals(withName.name(), name))
                .map(clazz::cast)
                .findFirst();
    }

    public <V extends ProtobufTree> Optional<? extends V> getAnyChildByNameAndType(String name, Class<V> clazz){
        if (name == null) {
            return Optional.empty();
        }

        return children.stream()
                .mapMulti((CHILD entry, Consumer<V> consumer) -> consumeChild(name, clazz, entry, consumer))
                .findFirst();
    }

    <V extends ProtobufTree> void consumeChild(String name, Class<V> clazz, ProtobufStatement entry, Consumer<V> consumer) {
        if(entry instanceof ProtobufTree.WithName withName && Objects.equals(withName.name(), name) && clazz.isAssignableFrom(entry.getClass())) {
            consumer.accept(clazz.cast(entry));
        }else if(entry instanceof ProtobufTree.WithBody<?> withBody && withBody.hasBody()) {
            withBody.body().consumeChild(name, clazz, entry, consumer);
        }
    }

    public boolean hasIndex(int index) {
        if(index < 0) {
            throw new IllegalArgumentException("Index cannot be negative");
        }

        return children.stream()
                .anyMatch(child -> hasIndex(index, child));
    }

    private static boolean hasIndex(int index, ProtobufStatement child) {
        return child instanceof ProtobufTree.WithIndex indexedTree
               && indexedTree.hasIndex()
               && indexedTree.index().value() == index;
    }

    public boolean hasName(String name) {
        Objects.requireNonNull(name, "Name cannot be null");
        return children.stream()
                .anyMatch(child -> hasName(name, child));
    }

    private static boolean hasName(String name, ProtobufStatement child) {
        return child instanceof ProtobufTree.WithName namedTree
               && Objects.equals(namedTree.name(), name);
    }

    public boolean isAttributed() {
        return children.stream().anyMatch(ProtobufTree::isAttributed);
    }
}

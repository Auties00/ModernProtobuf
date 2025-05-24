package it.auties.protobuf.parser.tree;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

@SuppressWarnings("BooleanMethodIsAlwaysInverted")
public sealed abstract class ProtobufBlock<T extends ProtobufBlock<T, C>, C extends ProtobufTree>
        extends ProtobufStatement
        permits ProtobufDocumentTree, ProtobufNameableBlock {
    protected final LinkedList<C> children;
    protected final boolean inheritsScope;
    protected ProtobufBlock(int line, boolean inheritsScope) {
        super(line);
        this.inheritsScope = inheritsScope;
        this.children = new LinkedList<>();
    }

    public boolean isScopeInherited() {
        return inheritsScope;
    }

    public abstract String qualifiedName();

    public abstract String qualifiedCanonicalName();

    public String qualifiedPath() {
        return qualifiedCanonicalName()
                .replaceAll("\\.", File.separator);
    }

    public SequencedCollection<C> children() {
        return Collections.unmodifiableSequencedCollection(children);
    }

    public ProtobufBlock<T, C> addChild(C statement){
        if(statement instanceof ProtobufStatement nestedTree) {
            nestedTree.setParent(this, 1);
        }

        children.add(statement);
        return this;
    }

    public void removeChild() {
        children.removeLast();
    }

    public void removeChild(C statement) {
        children.remove(statement);
    }

    private Stream<ProtobufNameableTree> getDirectChildrenByName(String name){
        if (name == null) {
            return Stream.empty();
        }

        return children.stream()
                .filter(entry -> entry instanceof ProtobufNameableTree)
                .map(entry -> (ProtobufNameableTree) entry)
                .filter(entry -> entry.name().filter(name::equals).isPresent());
    }

    public Optional<ProtobufNameableTree> getDirectChildByName(String name){
        return getDirectChildrenByName(name)
                .findFirst();
    }

    @SuppressWarnings("unchecked")
    public <V extends ProtobufTree> Optional<V> getDirectChildByNameAndType(String name, Class<V> clazz){
        return getDirectChildrenByName(name)
                .filter(entry -> clazz.isAssignableFrom(entry.getClass()))
                .findFirst()
                .map(entry -> (V) entry);
    }

    public <V extends ProtobufNameableTree> Optional<V> getAnyChildByNameAndType(String name, Class<V> clazz){
        var child = getDirectChildByNameAndType(name, clazz);
        if (child.isPresent()) {
            return child;
        }

        return children.stream()
                .filter(entry -> ProtobufBlock.class.isAssignableFrom(entry.getClass()))
                .map(entry -> (ProtobufBlock<?, ?>) entry)
                .flatMap(entry -> entry.getDirectChildByNameAndType(name, clazz).stream())
                .findFirst();
    }

    @SuppressWarnings({"unchecked"})
    public <V extends ProtobufTree> Optional<V> getAnyChildByType(Class<V> clazz){
        return children.stream()
                .map((entry) -> {
                    if(clazz.isAssignableFrom(entry.getClass())){
                        return Optional.of((V) entry);
                    }else if(entry instanceof ProtobufBlock<?, ?> object){
                        return object.getAnyChildByType(clazz);
                    } else {
                        return Optional.<V>empty();
                    }
                })
                .flatMap(Optional::stream)
                .findFirst();
    }

    public <V extends ProtobufTree> List<V> getAnyChildrenByType(Class<V> clazz){
        return children.stream()
                .mapMulti((ProtobufTree entry, Consumer<V> consumer) -> consumeChildren(entry, clazz, consumer))
                .toList();
    }

    @SuppressWarnings({"unchecked"})
    protected <V extends ProtobufTree> void consumeChildren(ProtobufTree child, Class<V> expectedType, Consumer<V> consumer) {
        if(expectedType.isAssignableFrom(child.getClass())){
            consumer.accept((V) child);
        }

        if(child instanceof ProtobufBlock<?, ?> objectChild){
            objectChild.consumeChildren(objectChild, expectedType, consumer);
        }
    }

    public boolean hasIndex(int index) {
        if(index < 0) {
            throw new IllegalArgumentException("Index cannot be negative");
        }

        for (var entry : children()) {
            if (!(entry instanceof ProtobufIndexableTree indexableTree)) {
                continue;
            }

            var entryIndex = indexableTree.index()
                    .orElse(-1);
            if (entryIndex != index) {
                continue;
            }

            return true;
        }

        return false;
    }

    public boolean hasName(String name) {
        Objects.requireNonNull(name, "Name cannot be null");
        for (var entry : children()) {
            if (!(entry instanceof ProtobufNameableTree nameableTree)) {
                continue;
            }

            var entryIndex = nameableTree.name()
                    .orElse(null);
            if (!name.equals(entryIndex)) {
                continue;
            }

            return true;
        }

        return false;
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();
        builder.append(toLeveledString("{"));
        children.forEach(statement -> {
            builder.append(statement);
            builder.append("\n");
        });
        builder.append(toLeveledString("}"));
        return builder.toString();
    }
}

package it.auties.protobuf.parser.tree;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

@SuppressWarnings("BooleanMethodIsAlwaysInverted")
public sealed abstract class ProtobufBlock<C extends ProtobufTree>
        extends ProtobufStatement
        permits ProtobufDocumentTree, ProtobufExtensionsListStatement, ProtobufNamedBlock, ProtobufReservedListStatement {
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

    @Override
    public boolean isAttributed() {
        return children.stream()
                .allMatch(ProtobufTree::isAttributed);
    }

    public SequencedCollection<C> children() {
        return Collections.unmodifiableSequencedCollection(children);
    }

    public void addChild(C statement){
        if(statement instanceof ProtobufStatement nestedTree) {
            nestedTree.setParent(this);
        }

        children.add(statement);
    }

    public void removeChild() {
        children.removeLast();
    }

    public void removeChild(C statement) {
        children.remove(statement);
    }

    private Stream<ProtobufNamedTree> getDirectChildrenByName(String name){
        if (name == null) {
            return Stream.empty();
        }

        return children.stream()
                .filter(entry -> entry instanceof ProtobufNamedTree)
                .map(entry -> (ProtobufNamedTree) entry)
                .filter(entry -> Objects.equals(entry.name(), name));
    }

    public Optional<ProtobufNamedTree> getDirectChildByName(String name){
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

    public <V extends ProtobufNamedTree> Optional<V> getAnyChildByNameAndType(String name, Class<V> clazz){
        var child = getDirectChildByNameAndType(name, clazz);
        if (child.isPresent()) {
            return child;
        }

        return children.stream()
                .filter(entry -> ProtobufBlock.class.isAssignableFrom(entry.getClass()))
                .map(entry -> (ProtobufBlock<?>) entry)
                .flatMap(entry -> entry.getDirectChildByNameAndType(name, clazz).stream())
                .findFirst();
    }

    @SuppressWarnings({"unchecked"})
    public <V extends ProtobufTree> Optional<V> getAnyChildByType(Class<V> clazz){
        return children.stream()
                .map((entry) -> {
                    if(clazz.isAssignableFrom(entry.getClass())){
                        return Optional.of((V) entry);
                    }else if(entry instanceof ProtobufBlock<?> object){
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

        if(child instanceof ProtobufBlock<?> objectChild){
            objectChild.consumeChildren(objectChild, expectedType, consumer);
        }
    }

    public boolean hasIndex(int index) {
        if(index < 0) {
            throw new IllegalArgumentException("Index cannot be negative");
        }

        return children()
                .stream()
                .filter(entry -> entry instanceof ProtobufIndexedTree)
                .anyMatch(entry -> (((ProtobufIndexedTree) entry).index()) == index);
    }

    public boolean hasName(String name) {
        Objects.requireNonNull(name, "Name cannot be null");
        return children()
                .stream()
                .filter(entry -> entry instanceof ProtobufNamedTree)
                .anyMatch(entry -> Objects.equals(name, ((ProtobufNamedTree) entry).name()));
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

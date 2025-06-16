package it.auties.protobuf.parser.tree;

import java.util.*;

public final class ProtobufTreeBody<CHILD extends ProtobufTree> {
    private final int line;
    private final LinkedList<CHILD> children;
    private final boolean inheritsScope;
    private final ProtobufTree owner;

    public ProtobufTreeBody(int line, boolean inheritsScope, ProtobufTree owner) {
        this.line = line;
        this.inheritsScope = inheritsScope;
        this.children = new LinkedList<>();
        this.owner = owner;
    }

    public int line() {
        return line;
    }

    public boolean isScopeInherited() {
        return inheritsScope;
    }

    public SequencedCollection<CHILD> children() {
        return Collections.unmodifiableSequencedCollection(children);
    }

    public ProtobufTree owner() {
        return owner;
    }

    public boolean hasOwner() {
        return owner != null;
    }

    void addChild(CHILD statement){
        children.add(statement);
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

    public boolean isAttributed() {
        return children.stream().anyMatch(ProtobufTree::isAttributed);
    }
}

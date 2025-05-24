package it.auties.protobuf.parser.tree;

import java.util.Objects;
import java.util.Optional;

public sealed abstract class ProtobufNameableBlock<T extends ProtobufNameableBlock<T, C>, C extends ProtobufTree>
        extends ProtobufBlock<T, C>
        implements ProtobufNameableTree
        permits ProtobufEnumTree, ProtobufGroupTree, ProtobufMessageTree, ProtobufOneofTree {
    protected String name;

    protected ProtobufNameableBlock(int line, String name, boolean inheritsScope) {
        super(line, inheritsScope);
        this.name = name;
    }

    @Override
    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    public ProtobufNameableBlock<T, C> setName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public String qualifiedName() {
        return parent()
                .map(parent -> parent.qualifiedName() + (parent instanceof ProtobufNameableBlock<?, ?> ? "$" : ".") + Objects.requireNonNullElse(name, "[missing]"))
                .orElse(name);
    }

    @Override
    public String qualifiedCanonicalName() {
        return parent()
                .map(parent -> parent.qualifiedCanonicalName() + "." + Objects.requireNonNullElse(name, "[missing]"))
                .orElse(name);
    }

    @Override
    public boolean isAttributed() {
        return name != null;
    }

    @Override
    public String toString() {
        var name = Objects.requireNonNullElse(this.name, "[missing]");
        return name + " " + super.toString();
    }
}

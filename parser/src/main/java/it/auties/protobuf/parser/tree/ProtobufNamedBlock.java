package it.auties.protobuf.parser.tree;

import java.io.File;
import java.util.Objects;

public sealed abstract class ProtobufNamedBlock<C extends ProtobufTree>
        extends ProtobufBlock<C>
        implements ProtobufNamedTree
        permits ProtobufEnumTree, ProtobufGroupTree, ProtobufMessageTree, ProtobufOneofTree {
    protected String name;

    protected ProtobufNamedBlock(int line, boolean inheritsScope) {
        super(line, inheritsScope);
    }

    @Override
    public String name() {
        return name;
    }

    public boolean hasName() {
        return name != null;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String qualifiedName() {
        var qualifiedName = new StringBuilder();
        ProtobufStatement current = this;
        while (current != null) {
            if(current instanceof ProtobufNamedBlock<?> namedBlock) {
                qualifiedName.insert(0, namedBlock.name() + ".");
            }
            current = current.parent;
        }
        return qualifiedName.toString();
    }

    public String qualifiedPath() {
        return qualifiedName()
                .replaceAll("\\.", File.separator);
    }

    @Override
    public boolean isAttributed() {
        return hasName();
    }

    @Override
    public String toString() {
        var name = Objects.requireNonNullElse(this.name, "[missing]");
        return name + " " + super.toString();
    }
}

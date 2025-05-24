package it.auties.protobuf.parser.tree;

import java.util.Objects;

public final class ProtobufEnumTree
        extends ProtobufNameableBlock<ProtobufEnumTree, ProtobufEnumChildTree>
        implements ProtobufDocumentChildTree, ProtobufMessageChildTree, ProtobufGroupChildTree {
    public ProtobufEnumTree(int line, String name) {
        super(line, name, false);
    }

    @Override
    public String toString() {
        var name = Objects.requireNonNullElse(this.name, "[missing]");
        return "enum " + name + " " + super.toString();
    }
}

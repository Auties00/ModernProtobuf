package it.auties.protobuf.parser.tree;

import java.util.Objects;

public final class ProtobufEnumTree
        extends ProtobufNamedBlock<ProtobufEnumChildTree>
        implements ProtobufDocumentChildTree, ProtobufMessageChildTree, ProtobufGroupChildTree {
    public ProtobufEnumTree(int line) {
        super(line, false);
    }

    @Override
    public String toString() {
        var name = Objects.requireNonNullElse(this.name, "<unknown>");
        return toLeveledString("enum " + name + " " + super.toString());
    }
}

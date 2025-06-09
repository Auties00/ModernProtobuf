package it.auties.protobuf.parser.tree;

import java.util.Locale;

public final class ProtobufOneofTree
        extends ProtobufNamedBlock<ProtobufOneofChildTree>
        implements ProtobufMessageChildTree, ProtobufGroupChildTree {
    public ProtobufOneofTree(int line) {
        super(line, false);
    }

    public String className() {
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1) + "Seal";
    }

    @Override
    public String toString() {
        return "oneof " + name + super.toString();
    }
}

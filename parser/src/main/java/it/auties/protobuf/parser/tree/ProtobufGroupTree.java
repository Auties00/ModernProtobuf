package it.auties.protobuf.parser.tree;

public final class ProtobufGroupTree
        extends ProtobufNameableBlock<ProtobufGroupTree, ProtobufGroupChildTree>
        implements ProtobufGroupChildTree, ProtobufMessageChildTree, ProtobufOneofChildTree {
    public ProtobufGroupTree(int line, String name) {
        super(line, name, false);
    }
}

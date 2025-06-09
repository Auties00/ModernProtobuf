package it.auties.protobuf.parser.tree;

public final class ProtobufGroupTree
        extends ProtobufNamedBlock<ProtobufGroupChildTree>
        implements ProtobufGroupChildTree, ProtobufMessageChildTree, ProtobufOneofChildTree {
    public ProtobufGroupTree(int line) {
        super(line, false);
    }
}

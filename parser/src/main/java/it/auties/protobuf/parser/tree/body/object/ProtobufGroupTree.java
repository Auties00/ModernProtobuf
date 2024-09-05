package it.auties.protobuf.parser.tree.body.object;

import it.auties.protobuf.parser.tree.body.oneof.ProtobufOneofChildTree;

public final class ProtobufGroupTree extends ProtobufObjectTree<ProtobufGroupTree, ProtobufGroupableChildTree> implements ProtobufGroupableChildTree, ProtobufOneofChildTree {
    public ProtobufGroupTree(int line, String name) {
        super(line, name);
    }

    @Override
    public boolean isAttributed() {
        return statements().stream().allMatch(ProtobufGroupableChildTree::isAttributed);
    }

    @Override
    public String toString() {
        return super.toString();
    }
}

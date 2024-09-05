package it.auties.protobuf.parser.tree.body.object;

import it.auties.protobuf.parser.tree.body.oneof.ProtobufOneofChildTree;

public final class ProtobufGroupTree extends ProtobufObjectTree<ProtobufGroupableChildTree> implements ProtobufGroupableChildTree, ProtobufOneofChildTree {
    public ProtobufGroupTree(String name) {
        super(name);
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

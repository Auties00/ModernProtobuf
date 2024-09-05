package it.auties.protobuf.parser.tree.body.object;

import it.auties.protobuf.parser.tree.body.document.ProtobufDocumentChildTree;

public final class ProtobufMessageTree extends ProtobufObjectTree<ProtobufGroupableChildTree> implements ProtobufDocumentChildTree, ProtobufGroupableChildTree {
    public ProtobufMessageTree(String name) {
        super(name);
    }

    @Override
    public boolean isAttributed() {
        return statements().stream().allMatch(ProtobufGroupableChildTree::isAttributed);
    }
}

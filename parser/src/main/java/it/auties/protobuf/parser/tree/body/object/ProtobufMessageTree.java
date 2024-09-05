package it.auties.protobuf.parser.tree.body.object;

import it.auties.protobuf.parser.tree.body.document.ProtobufDocumentChildTree;

public final class ProtobufMessageTree extends ProtobufObjectTree<ProtobufMessageTree, ProtobufGroupableChildTree> implements ProtobufDocumentChildTree, ProtobufGroupableChildTree {
    public ProtobufMessageTree(int line, String name) {
        super(line, name);
    }

    @Override
    public boolean isAttributed() {
        return statements().stream().allMatch(ProtobufGroupableChildTree::isAttributed);
    }
}

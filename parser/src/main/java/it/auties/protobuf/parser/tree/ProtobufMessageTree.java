package it.auties.protobuf.parser.tree;

public final class ProtobufMessageTree extends ProtobufObjectTree<ProtobufMessageChildTree> implements ProtobufDocumentChildTree, ProtobufMessageChildTree {
    public ProtobufMessageTree(String name) {
        super(name);
    }

    @Override
    public boolean isAttributed() {
        return statements().stream().allMatch(ProtobufMessageChildTree::isAttributed);
    }
}

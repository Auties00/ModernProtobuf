package it.auties.protobuf.parser.tree;

public final class ProtobufEnumTree extends ProtobufObjectTree<ProtobufEnumConstantTree> implements ProtobufDocumentChildTree, ProtobufMessageChildTree {
    public ProtobufEnumTree(String name) {
        super(name);
    }

    @Override
    public boolean isAttributed() {
        return statements().stream().allMatch(ProtobufEnumConstantTree::isAttributed);
    }
}

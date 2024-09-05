package it.auties.protobuf.parser.tree.body.object;

import it.auties.protobuf.parser.tree.body.document.ProtobufDocumentChildTree;
import it.auties.protobuf.parser.tree.nested.field.ProtobufEnumConstantTree;

public final class ProtobufEnumTree extends ProtobufObjectTree<ProtobufEnumConstantTree> implements ProtobufDocumentChildTree, ProtobufGroupableChildTree {
    public ProtobufEnumTree(String name) {
        super(name);
    }

    @Override
    public boolean isAttributed() {
        return statements().stream().allMatch(ProtobufEnumConstantTree::isAttributed);
    }
}

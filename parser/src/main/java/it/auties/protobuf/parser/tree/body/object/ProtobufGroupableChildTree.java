package it.auties.protobuf.parser.tree.body.object;

import it.auties.protobuf.parser.tree.nested.field.ProtobufGroupableFieldTree;
import it.auties.protobuf.parser.tree.body.oneof.ProtobufOneofTree;
import it.auties.protobuf.parser.tree.ProtobufTree;

public sealed interface ProtobufGroupableChildTree extends ProtobufTree permits ProtobufEnumTree, ProtobufGroupTree, ProtobufMessageTree, ProtobufGroupableFieldTree, ProtobufOneofTree {
    boolean isAttributed();
}

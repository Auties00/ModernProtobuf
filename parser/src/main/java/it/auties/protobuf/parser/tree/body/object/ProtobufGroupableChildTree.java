package it.auties.protobuf.parser.tree.body.object;

import it.auties.protobuf.parser.tree.ProtobufTree;
import it.auties.protobuf.parser.tree.body.oneof.ProtobufOneofTree;
import it.auties.protobuf.parser.tree.nested.field.ProtobufGroupableFieldTree;

public sealed interface ProtobufGroupableChildTree extends ProtobufTree permits ProtobufEnumTree, ProtobufGroupTree, ProtobufMessageTree, ProtobufGroupableFieldTree, ProtobufOneofTree {
    boolean isAttributed();
}

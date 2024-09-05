package it.auties.protobuf.parser.tree.body.oneof;

import it.auties.protobuf.parser.tree.body.object.ProtobufGroupTree;
import it.auties.protobuf.parser.tree.nested.field.ProtobufGroupableFieldTree;

public sealed interface ProtobufOneofChildTree permits ProtobufGroupableFieldTree, ProtobufGroupTree {

}

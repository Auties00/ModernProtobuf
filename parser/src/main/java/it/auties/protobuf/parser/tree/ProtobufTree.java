package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.tree.body.document.ProtobufDocumentChildTree;
import it.auties.protobuf.parser.tree.body.object.ProtobufGroupableChildTree;
import it.auties.protobuf.parser.tree.nested.ProtobufNestedTree;

public sealed interface ProtobufTree permits ProtobufNamedTree, ProtobufDocumentChildTree, ProtobufGroupableChildTree, ProtobufNestedTree {
}

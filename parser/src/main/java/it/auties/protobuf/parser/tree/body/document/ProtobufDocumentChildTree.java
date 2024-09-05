package it.auties.protobuf.parser.tree.body.document;

import it.auties.protobuf.parser.tree.ProtobufTree;
import it.auties.protobuf.parser.tree.body.object.ProtobufEnumTree;
import it.auties.protobuf.parser.tree.body.object.ProtobufMessageTree;
import it.auties.protobuf.parser.tree.nested.impors.ProtobufImportTree;

public sealed interface ProtobufDocumentChildTree extends ProtobufTree permits ProtobufImportTree, ProtobufMessageTree, ProtobufEnumTree {
    boolean isAttributed();
}

package it.auties.protobuf.parser.tree;

public sealed interface ProtobufDocumentChildTree extends ProtobufTree permits ProtobufImportTree, ProtobufMessageTree, ProtobufEnumTree {
    boolean isAttributed();
}

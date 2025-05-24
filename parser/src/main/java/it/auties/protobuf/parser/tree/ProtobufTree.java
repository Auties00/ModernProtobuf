package it.auties.protobuf.parser.tree;

public sealed interface ProtobufTree
        permits ProtobufDocumentChildTree, ProtobufEnumChildTree, ProtobufGroupChildTree, ProtobufMessageChildTree, ProtobufOneofChildTree,
                ProtobufIndexableTree, ProtobufNameableTree,
                ProtobufStatement {
    boolean isAttributed();
    int line();
}

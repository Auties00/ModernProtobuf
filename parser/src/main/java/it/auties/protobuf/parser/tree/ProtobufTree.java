package it.auties.protobuf.parser.tree;

public sealed interface ProtobufTree
        permits ProtobufDocumentChildTree, ProtobufEnumChildTree, ProtobufGroupChildTree, ProtobufMessageChildTree, ProtobufOneofChildTree,
        ProtobufIndexedTree, ProtobufNamedTree,
                ProtobufStatement {
    int line();
    boolean isAttributed();
}

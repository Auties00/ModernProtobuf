package it.auties.protobuf.parser.tree;

public sealed interface ProtobufTree
        permits ProtobufDocumentChild, ProtobufDocument, ProtobufEnumChild, ProtobufGroupChild,
                ProtobufIndexedTree, ProtobufMessageChild, ProtobufMethodChild, ProtobufNamedTree,
        ProtobufOneofChild, ProtobufServiceChild, ProtobufStatement {
    int line();
    boolean isAttributed();
}

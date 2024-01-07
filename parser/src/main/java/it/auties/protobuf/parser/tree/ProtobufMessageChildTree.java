package it.auties.protobuf.parser.tree;

public sealed interface ProtobufMessageChildTree extends ProtobufTree permits ProtobufMessageTree, ProtobufEnumTree, ProtobufOneOfTree, ProtobufModifiableFieldTree {
    boolean isAttributed();
}

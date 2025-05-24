package it.auties.protobuf.parser.tree;

public sealed interface ProtobufGroupChildTree extends ProtobufTree
        permits ProtobufEnumTree, ProtobufExtensionsStatement, ProtobufFieldStatement, ProtobufGroupTree, ProtobufMessageTree, ProtobufOneofTree, ProtobufOptionStatement, ProtobufReservedStatement {

}

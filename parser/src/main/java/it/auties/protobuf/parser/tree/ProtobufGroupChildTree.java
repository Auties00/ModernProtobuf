package it.auties.protobuf.parser.tree;

public sealed interface ProtobufGroupChildTree extends ProtobufTree
        permits ProtobufEmptyStatement, ProtobufEnumTree, ProtobufExtensionsListStatement, ProtobufFieldStatement, ProtobufGroupTree, ProtobufMessageTree, ProtobufOneofTree, ProtobufOptionStatement, ProtobufReservedListStatement {

}

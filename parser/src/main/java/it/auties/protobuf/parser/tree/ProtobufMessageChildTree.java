package it.auties.protobuf.parser.tree;

public sealed interface ProtobufMessageChildTree
        extends ProtobufTree
        permits ProtobufEnumTree, ProtobufExtensionsStatement, ProtobufFieldStatement, ProtobufGroupTree, ProtobufMessageTree, ProtobufOneofTree, ProtobufOptionStatement, ProtobufReservedStatement {

}

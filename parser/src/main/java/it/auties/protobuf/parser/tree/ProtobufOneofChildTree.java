package it.auties.protobuf.parser.tree;

public sealed interface ProtobufOneofChildTree
        extends ProtobufTree
        permits ProtobufEmptyStatement, ProtobufFieldStatement, ProtobufGroupTree, ProtobufOptionStatement {

}

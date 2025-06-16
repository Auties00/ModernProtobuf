package it.auties.protobuf.parser.tree;

public sealed interface ProtobufOneofChild
        extends ProtobufTree
        permits ProtobufEmptyStatement, ProtobufField, ProtobufGroupField, ProtobufOption {

}

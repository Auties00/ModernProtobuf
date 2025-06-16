package it.auties.protobuf.parser.tree;

public sealed interface ProtobufMethodChild
        extends ProtobufTree
        permits ProtobufEmptyStatement, ProtobufOption {

}

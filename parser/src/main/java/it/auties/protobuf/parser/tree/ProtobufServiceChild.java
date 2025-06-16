package it.auties.protobuf.parser.tree;

public sealed interface ProtobufServiceChild
        extends ProtobufTree
        permits ProtobufOption, ProtobufMethod, ProtobufEmptyStatement {

}

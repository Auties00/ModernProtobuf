package it.auties.protobuf.parser.tree;

public sealed interface ProtobufMethodChild
        extends ProtobufStatement
        permits ProtobufEmptyStatement, ProtobufOption {

}

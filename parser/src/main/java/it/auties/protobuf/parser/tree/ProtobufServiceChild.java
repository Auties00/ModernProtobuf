package it.auties.protobuf.parser.tree;

public sealed interface ProtobufServiceChild
        extends ProtobufStatement
        permits ProtobufOption, ProtobufMethod, ProtobufEmptyStatement {

}

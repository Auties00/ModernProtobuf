package it.auties.protobuf.parser.tree;

public sealed interface ProtobufOneofChild
        extends ProtobufStatement
        permits ProtobufEmptyStatement, ProtobufField, ProtobufGroupField, ProtobufOption {

}

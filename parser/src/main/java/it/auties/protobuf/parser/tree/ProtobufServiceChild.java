package it.auties.protobuf.parser.tree;

public sealed interface ProtobufServiceChild
        extends ProtobufStatement
        permits ProtobufOptionStatement, ProtobufMethodStatement, ProtobufEmptyStatement {

}

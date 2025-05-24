package it.auties.protobuf.parser.tree;

public sealed interface ProtobufEnumChildTree
        extends ProtobufTree
        permits ProtobufEnumConstantStatement, ProtobufExtensionsStatement, ProtobufOptionStatement, ProtobufReservedStatement {

}

package it.auties.protobuf.parser.tree;

public sealed interface ProtobufEnumChildTree
        extends ProtobufTree
        permits ProtobufEmptyStatement, ProtobufEnumConstantStatement, ProtobufExtensionsListStatement, ProtobufOptionStatement, ProtobufReservedListStatement {

}

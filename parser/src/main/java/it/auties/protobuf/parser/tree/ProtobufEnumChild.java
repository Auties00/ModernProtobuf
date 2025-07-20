package it.auties.protobuf.parser.tree;

public sealed interface ProtobufEnumChild
        extends ProtobufStatement
        permits ProtobufEmptyStatement, ProtobufEnumConstantStatement, ProtobufExtensionsStatement, ProtobufOptionStatement, ProtobufReservedStatement {

}

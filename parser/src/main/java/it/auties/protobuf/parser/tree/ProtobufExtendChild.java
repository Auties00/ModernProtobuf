package it.auties.protobuf.parser.tree;

public sealed interface ProtobufExtendChild
        extends ProtobufStatement
        permits ProtobufEmptyStatement, ProtobufEnumStatement, ProtobufExtendStatement, ProtobufExtensionsStatement, ProtobufFieldStatement, ProtobufGroupFieldStatement, ProtobufMessageStatement, ProtobufOneofFieldStatement, ProtobufOptionStatement, ProtobufReservedStatement {

}

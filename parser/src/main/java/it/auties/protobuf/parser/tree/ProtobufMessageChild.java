package it.auties.protobuf.parser.tree;

public sealed interface ProtobufMessageChild
        extends ProtobufStatement
        permits ProtobufEmptyStatement, ProtobufEnumStatement, ProtobufExtensionsStatement, ProtobufFieldStatement, ProtobufGroupFieldStatement, ProtobufMessageStatement, ProtobufOneofFieldStatement, ProtobufOptionStatement, ProtobufReservedStatement {

}

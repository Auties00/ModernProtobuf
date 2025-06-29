package it.auties.protobuf.parser.tree;

public sealed interface ProtobufGroupChild
        extends ProtobufStatement
        permits ProtobufEmptyStatement, ProtobufEnumStatement, ProtobufExtensionsStatement, ProtobufFieldStatement, ProtobufGroupFieldStatement, ProtobufMessageStatement, ProtobufOneofStatement, ProtobufReservedStatement {

}

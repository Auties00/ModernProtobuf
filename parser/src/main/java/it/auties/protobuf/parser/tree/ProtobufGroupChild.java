package it.auties.protobuf.parser.tree;

public sealed interface ProtobufGroupChild
        extends ProtobufStatement
        permits ProtobufEmptyStatement, ProtobufEnum, ProtobufExtensionsList, ProtobufField, ProtobufGroupField, ProtobufMessage, ProtobufOneof, ProtobufReservedList {

}

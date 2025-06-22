package it.auties.protobuf.parser.tree;

public sealed interface ProtobufMessageChild
        extends ProtobufStatement
        permits ProtobufEmptyStatement, ProtobufEnum, ProtobufExtensionsList, ProtobufField, ProtobufGroupField, ProtobufMessage, ProtobufOneof, ProtobufOption, ProtobufReservedList {

}

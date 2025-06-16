package it.auties.protobuf.parser.tree;

public sealed interface ProtobufMessageChild
        extends ProtobufTree
        permits ProtobufEmptyStatement, ProtobufEnum, ProtobufExtensionsList, ProtobufField, ProtobufGroupField, ProtobufMessage, ProtobufOneof, ProtobufOption, ProtobufReservedList {

}

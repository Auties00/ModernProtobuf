package it.auties.protobuf.parser.tree;

public sealed interface ProtobufEnumChild
        extends ProtobufTree
        permits ProtobufEmptyStatement, ProtobufEnumConstant, ProtobufExtensionsList, ProtobufOption, ProtobufReservedList {

}

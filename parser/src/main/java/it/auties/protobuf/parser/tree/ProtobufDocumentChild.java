package it.auties.protobuf.parser.tree;

public sealed interface ProtobufDocumentChild
        extends ProtobufTree
        permits ProtobufEmptyStatement, ProtobufEnum, ProtobufImport, ProtobufMessage, ProtobufOption, ProtobufPackage, ProtobufService, ProtobufSyntax {

}

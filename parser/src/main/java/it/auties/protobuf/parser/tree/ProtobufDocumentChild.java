package it.auties.protobuf.parser.tree;

public sealed interface ProtobufDocumentChild
        extends ProtobufStatement
        permits ProtobufEmptyStatement, ProtobufEnum, ProtobufImport, ProtobufMessage, ProtobufOption, ProtobufPackage, ProtobufService, ProtobufSyntax {

}

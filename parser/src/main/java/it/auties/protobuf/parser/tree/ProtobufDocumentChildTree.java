package it.auties.protobuf.parser.tree;

public sealed interface ProtobufDocumentChildTree
        extends ProtobufTree
        permits ProtobufEnumTree, ProtobufMessageTree, ProtobufImportStatement, ProtobufOptionStatement, ProtobufPackageStatement, ProtobufSyntaxStatement {

}

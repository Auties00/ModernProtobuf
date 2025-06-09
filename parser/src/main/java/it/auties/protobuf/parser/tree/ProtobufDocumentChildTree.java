package it.auties.protobuf.parser.tree;

public sealed interface ProtobufDocumentChildTree
        extends ProtobufTree
        permits ProtobufEmptyStatement, ProtobufEnumTree, ProtobufImportStatement, ProtobufMessageTree, ProtobufOptionStatement, ProtobufPackageStatement, ProtobufSyntaxStatement {

}

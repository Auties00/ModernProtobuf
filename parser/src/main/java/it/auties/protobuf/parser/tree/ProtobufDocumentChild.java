package it.auties.protobuf.parser.tree;

public sealed interface ProtobufDocumentChild
        extends ProtobufStatement
        permits ProtobufEmptyStatement, ProtobufEnumStatement, ProtobufImportStatement, ProtobufMessageStatement, ProtobufOptionStatement, ProtobufPackageStatement, ProtobufServiceStatement, ProtobufSyntaxStatement {

}

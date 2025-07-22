package it.auties.protobuf.parser.tree;

public sealed interface ProtobufDocumentChild
        extends ProtobufStatement
        permits ProtobufEmptyStatement, ProtobufEnumStatement, ProtobufExtendStatement, ProtobufImportStatement, ProtobufMessageStatement, ProtobufOptionStatement, ProtobufPackageStatement, ProtobufServiceStatement, ProtobufSyntaxStatement {

}

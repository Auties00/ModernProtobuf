package it.auties.protobuf.parser.tree;

public sealed interface ProtobufStatement
        extends ProtobufTree
        permits ProtobufDocumentChild, ProtobufEmptyStatement, ProtobufEnumChild, ProtobufEnumStatement, ProtobufExtensionsStatement, ProtobufFieldStatement, ProtobufGroupChild, ProtobufImportStatement, ProtobufMessageChild, ProtobufMessageStatement, ProtobufMethodChild, ProtobufMethodStatement, ProtobufOneofChild, ProtobufOneofStatement, ProtobufOptionStatement, ProtobufPackageStatement, ProtobufReservedStatement, ProtobufServiceChild, ProtobufServiceStatement, ProtobufStatementImpl, ProtobufSyntaxStatement {
    @Override
    ProtobufTree.WithBody<?> parent();
}

package it.auties.protobuf.parser.tree;

public sealed interface ProtobufStatement
        extends ProtobufTree
        permits ProtobufDocumentChild, ProtobufEmptyStatement, ProtobufEnum, ProtobufEnumChild, ProtobufExtension, ProtobufExtensionsList, ProtobufField, ProtobufGroupChild, ProtobufImport, ProtobufMessage, ProtobufMessageChild, ProtobufMethod, ProtobufMethodChild, ProtobufMutableStatement, ProtobufOneof, ProtobufOneofChild, ProtobufOption, ProtobufPackage, ProtobufReserved, ProtobufReservedList, ProtobufService, ProtobufServiceChild, ProtobufSyntax {
    @Override
    ProtobufTree.WithBody<?> parent();
}

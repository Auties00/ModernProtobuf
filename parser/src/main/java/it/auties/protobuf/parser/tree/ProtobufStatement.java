package it.auties.protobuf.parser.tree;

public sealed interface ProtobufStatement
        extends ProtobufTree
        permits ProtobufEmptyStatement, ProtobufEnum, ProtobufExtension, ProtobufExtensionsList, ProtobufField, ProtobufImport, ProtobufMessage, ProtobufMethod, ProtobufOneof, ProtobufOption, ProtobufPackage, ProtobufReserved, ProtobufReservedList, ProtobufService, ProtobufSyntax, ProtobufDocumentChild, ProtobufEnumChild, ProtobufGroupChild, ProtobufMessageChild, ProtobufMethodChild, ProtobufOneofChild, ProtobufServiceChild {
    ProtobufTree parent();
    boolean hasParent();
    void setParent(ProtobufTree tree);
}

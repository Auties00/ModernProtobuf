package it.auties.protobuf.parser.tree;

sealed abstract class ProtobufMutableStatement
        implements ProtobufStatement
        permits ProtobufEmptyStatement, ProtobufEnum, ProtobufExtension, ProtobufExtensionsList, ProtobufField, ProtobufImport, ProtobufMessage, ProtobufMethod, ProtobufOneof, ProtobufOption, ProtobufPackage, ProtobufReserved, ProtobufReservedList, ProtobufService, ProtobufSyntax {
    private final int line;
    private ProtobufTree.WithBody<?> parent;

    ProtobufMutableStatement(int line) {
        this.line = line;
    }

    @Override
    public int line() {
        return line;
    }

    @Override
    public ProtobufTree.WithBody<?> parent() {
        return parent;
    }

    @Override
    public boolean hasParent() {
        return parent != null;
    }

    void setParent(ProtobufTree.WithBody<?> parent) {
        this.parent = parent;
    }
}

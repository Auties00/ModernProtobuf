package it.auties.protobuf.parser.tree;

// Used to provide a package-private setParent method
// ProtobufStatement can't provide non-public methods as it's an interface
sealed abstract class ProtobufStatementImpl
        implements ProtobufStatement
        permits ProtobufEmptyStatement, ProtobufEnumStatement, ProtobufExtensionsStatement, ProtobufFieldStatement, ProtobufImportStatement, ProtobufMessageStatement, ProtobufMethodStatement, ProtobufOneofStatement, ProtobufOptionStatement, ProtobufPackageStatement, ProtobufReservedStatement, ProtobufServiceStatement, ProtobufSyntaxStatement {
    final int line;
    ProtobufTree.WithBody<?> parent;

    ProtobufStatementImpl(int line) {
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

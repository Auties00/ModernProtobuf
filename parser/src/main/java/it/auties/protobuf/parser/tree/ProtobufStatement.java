package it.auties.protobuf.parser.tree;

public sealed abstract class ProtobufStatement
        implements ProtobufTree
        permits ProtobufBlock, ProtobufEmptyStatement, ProtobufExtensionStatement, ProtobufFieldStatement, ProtobufImportStatement, ProtobufOptionStatement, ProtobufPackageStatement, ProtobufReservedStatement, ProtobufSyntaxStatement {
    private final int line;
    protected ProtobufBlock<?> parent;
    protected int level;
    protected ProtobufStatement(int line) {
        this.line = line;
    }

    @Override
    public int line() {
        return line;
    }

    public ProtobufBlock<?> parent() {
        return parent;
    }

    public boolean hasParent() {
        return parent != null;
    }

    public void setParent(ProtobufBlock<?> parent) {
        this.parent = parent;
        this.level = parent.level;
    }

    // TODO: Print options?
    protected String optionsToString() {
        return "";
    }

    protected String toLeveledString(String input) {
        return "    ".repeat(this.level == 0 ? 0 : this.level - 1) + input;
    }
}

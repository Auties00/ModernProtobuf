package it.auties.protobuf.parser.tree;

public final class ProtobufEmptyStatement
        implements ProtobufStatement,
                   ProtobufDocumentChild, ProtobufMessageChild, ProtobufEnumChild, ProtobufGroupChild, ProtobufOneofChild, ProtobufMethodChild, ProtobufServiceChild {
    private final int line;
    private ProtobufTree parent;
    public ProtobufEmptyStatement(int line) {
        this.line = line;
    }

    @Override
    public ProtobufTree parent() {
        return parent;
    }

    @Override
    public boolean hasParent() {
        return parent != null;
    }

    @Override
    public void setParent(ProtobufTree parent) {
        this.parent = parent;
    }

    @Override
    public int line() {
        return line;
    }

    @Override
    public boolean isAttributed() {
        return true;
    }

    @Override
    public String toString() {
        return ";";
    }
}
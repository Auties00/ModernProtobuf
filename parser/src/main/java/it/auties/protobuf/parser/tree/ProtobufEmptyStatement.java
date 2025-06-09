package it.auties.protobuf.parser.tree;

public final class ProtobufEmptyStatement
        extends ProtobufStatement
        implements ProtobufDocumentChildTree, ProtobufMessageChildTree, ProtobufEnumChildTree, ProtobufGroupChildTree, ProtobufOneofChildTree {
    public ProtobufEmptyStatement(int line) {
        super(line);
    }

    @Override
    public boolean isAttributed() {
        return true;
    }

    @Override
    public String toString() {
        return toLeveledString(";");
    }
}

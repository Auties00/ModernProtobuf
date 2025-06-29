package it.auties.protobuf.parser.tree;

public final class ProtobufEmptyStatement
        extends ProtobufStatementImpl
        implements ProtobufStatement,
                   ProtobufDocumentChild, ProtobufMessageChild, ProtobufEnumChild, ProtobufGroupChild, ProtobufOneofChild, ProtobufMethodChild, ProtobufServiceChild {
    public ProtobufEmptyStatement(int line) {
        super(line);
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
package it.auties.protobuf.parser.tree;

public final class ProtobufExtensionsListStatement
        extends ProtobufBlock<ProtobufExtensionStatement>
        implements ProtobufMessageChildTree, ProtobufEnumChildTree, ProtobufGroupChildTree {
    public ProtobufExtensionsListStatement(int line) {
        super(line, false);
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();
        builder.append(toLeveledString("reserved "));
        children.forEach(builder::append);
        builder.append(";");
        return builder.toString();
    }
}

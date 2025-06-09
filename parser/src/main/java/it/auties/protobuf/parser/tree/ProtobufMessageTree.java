package it.auties.protobuf.parser.tree;

public final class ProtobufMessageTree
        extends ProtobufNamedBlock<ProtobufMessageChildTree>
        implements ProtobufDocumentChildTree, ProtobufGroupChildTree, ProtobufMessageChildTree {
    private final boolean extension;
    public ProtobufMessageTree(int line, boolean extension) {
        super(line, false);
        this.extension = extension;
    }

    public boolean isExtension() {
        return extension;
    }

    @Override
    public String toString() {
        return "message " + name + " " + super.toString();
    }
}

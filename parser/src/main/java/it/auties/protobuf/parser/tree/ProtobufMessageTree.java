package it.auties.protobuf.parser.tree;

public final class ProtobufMessageTree
        extends ProtobufNameableBlock<ProtobufMessageTree, ProtobufMessageChildTree>
        implements ProtobufDocumentChildTree, ProtobufGroupChildTree, ProtobufMessageChildTree {
    private final boolean extension;
    public ProtobufMessageTree(int line, String name, boolean extension) {
        super(line, name, false);
        this.extension = extension;
    }

    public boolean isExtension() {
        return extension;
    }

    @Override
    public boolean isAttributed() {
        return children().stream().allMatch(ProtobufTree::isAttributed);
    }

    @Override
    public String toString() {
        return "message " + name + " " + super.toString();
    }
}

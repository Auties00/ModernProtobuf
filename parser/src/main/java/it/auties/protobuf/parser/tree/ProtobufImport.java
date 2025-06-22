package it.auties.protobuf.parser.tree;

import java.util.Objects;

public final class ProtobufImport
        implements ProtobufStatement,
                   ProtobufDocumentChild {
    private final int line;
    private String location;
    private ProtobufDocument document;
    private ProtobufTree parent;

    public ProtobufImport(int line) {
        this.line = line;
    }

    @Override
    public int line() {
        return line;
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

    public String location() {
        return location;
    }

    public boolean hasLocation() {
        return location != null;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public ProtobufDocument document() {
        return document;
    }

    public boolean hasDocument() {
        return document != null;
    }

    public void setDocument(ProtobufDocument document) {
        this.document = document;
    }

    @Override
    public String toString() {
        return "import \"" + location + "\";";
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(location);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof ProtobufImport that
                && Objects.equals(this.location(), that.location());
    }

    @Override
    public boolean isAttributed() {
        return hasDocument();
    }
}

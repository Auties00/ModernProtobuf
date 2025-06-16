package it.auties.protobuf.parser.tree;

import java.util.Objects;

public final class ProtobufImport
        extends ProtobufStatement
        implements ProtobufDocumentChild {
    private String location;
    private ProtobufDocument document;

    public ProtobufImport(int line, ProtobufDocument parent) {
        super(line, parent.body());
        Objects.requireNonNull(parent, "parent cannot be null");
        parent.body()
                .addChild(this);
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

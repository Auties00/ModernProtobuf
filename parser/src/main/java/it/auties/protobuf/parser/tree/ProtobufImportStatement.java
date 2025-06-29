package it.auties.protobuf.parser.tree;

import java.util.Objects;

public final class ProtobufImportStatement
        extends ProtobufStatementImpl
        implements ProtobufStatement,
                   ProtobufDocumentChild {
    private String location;
    private ProtobufDocumentTree document;

    public ProtobufImportStatement(int line) {
        super(line);
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

    public ProtobufDocumentTree document() {
        return document;
    }

    public boolean hasDocument() {
        return document != null;
    }

    public void setDocument(ProtobufDocumentTree document) {
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
        return this == obj || obj instanceof ProtobufImportStatement that
                && Objects.equals(this.location(), that.location());
    }

    @Override
    public boolean isAttributed() {
        return hasDocument();
    }
}

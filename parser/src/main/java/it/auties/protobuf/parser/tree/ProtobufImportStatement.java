package it.auties.protobuf.parser.tree;

import java.util.Objects;
import java.util.Optional;

public final class ProtobufImportStatement
        extends ProtobufStatement
        implements ProtobufDocumentChildTree {
    private final String location;
    private ProtobufDocumentTree document;

    public ProtobufImportStatement(int line, String location) {
        super(line);
        this.location = Objects.requireNonNull(location);
    }

    public String location() {
        return location;
    }

    public Optional<ProtobufDocumentTree> document() {
        return Optional.ofNullable(document);
    }

    public ProtobufImportStatement setDocument(ProtobufDocumentTree document) {
        this.document = document;
        return this;
    }

    @Override
    public String toString() {
        var imported = document != null
                ? document.qualifiedCanonicalName()
                : location;
        return "import \"" + imported + "\";";
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
        return document != null;
    }
}

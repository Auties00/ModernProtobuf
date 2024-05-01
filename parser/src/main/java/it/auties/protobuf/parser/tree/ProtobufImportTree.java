package it.auties.protobuf.parser.tree;

import java.util.Objects;
import java.util.Optional;

public final class ProtobufImportTree extends ProtobufNestedTree implements ProtobufDocumentChildTree {
    private final String location;
    private ProtobufDocument document;

    public ProtobufImportTree(String location) {
        this.location = Objects.requireNonNull(location);
    }

    public String location() {
        return location;
    }

    public Optional<ProtobufDocument> document() {
        return Optional.ofNullable(document);
    }

    public ProtobufImportTree setDocument(ProtobufDocument document) {
        this.document = document;
        return this;
    }

    @Override
    public String toString() {
        var path = Optional.ofNullable(document)
                .flatMap(ProtobufDocument::qualifiedPath)
                .map(entry -> "\"" + entry + "\"")
                .orElse(location);
        return "import " + path + ";";
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(location);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof ProtobufImportTree that
                && Objects.equals(this.location(), that.location());
    }

    @Override
    public boolean isAttributed() {
        return document != null;
    }
}

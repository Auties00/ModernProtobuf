package it.auties.protobuf.parser.tree.nested.imports;

import it.auties.protobuf.parser.tree.body.document.ProtobufDocumentTree;
import it.auties.protobuf.parser.tree.body.document.ProtobufDocumentChildTree;
import it.auties.protobuf.parser.tree.nested.ProtobufNestedTree;

import java.util.Objects;
import java.util.Optional;

public final class ProtobufImportTree extends ProtobufNestedTree implements ProtobufDocumentChildTree {
    private final String location;
    private ProtobufDocumentTree document;

    public ProtobufImportTree(int line, String location) {
        super(line);
        this.location = Objects.requireNonNull(location);
    }

    public String location() {
        return location;
    }

    public Optional<ProtobufDocumentTree> document() {
        return Optional.ofNullable(document);
    }

    public ProtobufImportTree setDocument(ProtobufDocumentTree document) {
        this.document = document;
        return this;
    }

    @Override
    public String toString() {
        var path = Optional.ofNullable(document)
                .flatMap(ProtobufDocumentTree::qualifiedPath)
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

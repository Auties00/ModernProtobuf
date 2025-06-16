package it.auties.protobuf.parser.tree;

import it.auties.protobuf.model.ProtobufVersion;

import java.nio.file.Path;
import java.util.Optional;

public final class ProtobufDocument implements ProtobufTree {
    private final Path location;
    private final ProtobufTreeBody<ProtobufDocumentChild> body;
    public ProtobufDocument(Path location) {
        this.location = location;
        this.body = new ProtobufTreeBody<>(0, true, null);
    }

    @Override
    public int line() {
        return body.line();
    }

    @Override
    public boolean isAttributed() {
        return body.isAttributed();
    }

    public Path location() {
        return location;
    }

    public ProtobufTreeBody<ProtobufDocumentChild> body() {
        return body;
    }

    public Optional<ProtobufVersion> syntax() {
        return body.children()
                .stream()
                .filter(statement -> statement instanceof ProtobufSyntax)
                .map(statement -> (ProtobufSyntax) statement)
                .map(ProtobufSyntax::version)
                .findFirst();
    }

    public Optional<String> packageName() {
        return body.children()
                .stream()
                .filter(statement -> statement instanceof ProtobufPackage)
                .map(statement -> (ProtobufPackage) statement)
                .map(ProtobufPackage::name)
                .findFirst();
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();
        body.children().forEach(statement -> {
            builder.append(statement);
            builder.append("\n");
        });
        return builder.toString();
    }
}

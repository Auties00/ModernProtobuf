package it.auties.protobuf.parser.tree;

import it.auties.protobuf.model.ProtobufVersion;
import it.auties.protobuf.parser.tree.ProtobufExpression.Value.Literal;

import java.nio.file.Path;
import java.util.Optional;

public final class ProtobufDocument
        implements ProtobufTree, ProtobufTree.WithBody<ProtobufDocumentChild> {
    private final Path location;
    private final ProtobufBody<ProtobufDocumentChild> body;
    public ProtobufDocument(Path location) {
        this.location = location;
        this.body = new ProtobufBody<>(0);
        body.setOwner(this);
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

    @Override
    public ProtobufBody<ProtobufDocumentChild> body() {
        return body;
    }

    @Override
    public boolean hasBody() {
        return true;
    }

    @Override
    public void setBody(ProtobufBody<ProtobufDocumentChild> body) {
        throw new UnsupportedOperationException("Cannot set the body of a document");
    }

    public String qualifiedPath() {
        return packageName()
                .map(packageName -> packageName.replaceAll("\\.", "/") + "/" + location.getFileName().toString())
                .orElse(location.getFileName().toString());
    }

    public Optional<ProtobufVersion> syntax() {
        for(var child : body.children()){
            if (!(child instanceof ProtobufSyntax syntax) || !syntax.hasVersion()) {
                continue;
            }

            if(!(syntax.version().value() instanceof Literal(var value))) {
                continue;
            }

            return ProtobufVersion.of(value);
        }
        return Optional.empty();
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

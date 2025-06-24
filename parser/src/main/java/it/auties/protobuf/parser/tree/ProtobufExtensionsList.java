package it.auties.protobuf.parser.tree;

import java.util.stream.Collectors;

public final class ProtobufExtensionsList
        extends ProtobufMutableStatement
        implements ProtobufStatement, ProtobufTree.WithBody<ProtobufExtension>,
                   ProtobufMessageChild, ProtobufEnumChild, ProtobufGroupChild {
    private final ProtobufBody<ProtobufExtension> body;

    public ProtobufExtensionsList(int line) {
        super(line);
        this.body = new ProtobufBody<>(line);
        body.setOwner(this);
    }

    public ProtobufBody<ProtobufExtension> body() {
        return body;
    }

    @Override
    public boolean hasBody() {
        return body != null;
    }

    public void setBody(ProtobufBody<ProtobufExtension> body) {
        throw new UnsupportedOperationException("Cannot set the body of a extensions list");
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();

        builder.append("extensions");
        builder.append(" ");

        var values = body.children()
                .stream()
                .map(ProtobufExtension::toString)
                .collect(Collectors.joining(", "));
        builder.append(values);

        builder.append(";");

        return builder.toString();
    }

    @Override
    public boolean isAttributed() {
        return !body.children().isEmpty() && body.children().stream().allMatch(ProtobufTree::isAttributed);
    }
}

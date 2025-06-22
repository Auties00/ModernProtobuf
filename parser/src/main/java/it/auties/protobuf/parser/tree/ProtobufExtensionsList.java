package it.auties.protobuf.parser.tree;

import java.util.stream.Collectors;

public final class ProtobufExtensionsList
        implements ProtobufStatement,
                   ProtobufMessageChild, ProtobufEnumChild, ProtobufGroupChild {
    private final int line;
    private final ProtobufBody<ProtobufExtension> body;
    private ProtobufTree parent;

    public ProtobufExtensionsList(int line) {
        this.line = line;
        this.body = new ProtobufBody<>(line);
        body.setOwner(this);
    }

    @Override
    public int line() {
        return line;
    }

    public ProtobufBody<ProtobufExtension> body() {
        return body;
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

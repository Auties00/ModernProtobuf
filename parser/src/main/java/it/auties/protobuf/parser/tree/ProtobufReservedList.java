package it.auties.protobuf.parser.tree;

import java.util.stream.Collectors;

public final class ProtobufReservedList
        implements ProtobufStatement,
        ProtobufMessageChild, ProtobufEnumChild, ProtobufGroupChild {
    private final int line;
    private final ProtobufBody<ProtobufReserved> body;
    private ProtobufTree parent;

    public ProtobufReservedList(int line) {
        this.line = line;
        this.body = new ProtobufBody<>(line);
        body.setOwner(this);
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

    public ProtobufBody<ProtobufReserved> body() {
        return body;
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();

        builder.append("reserved");
        builder.append(" ");

        var values = body.children()
                .stream()
                .map(ProtobufReserved::toString)
                .collect(Collectors.joining(", "));
        builder.append(values);

        builder.append(";");

        return builder.toString();
    }

    @Override
    public boolean isAttributed() {
        return !body.children().isEmpty() && body.isAttributed();
    }
}

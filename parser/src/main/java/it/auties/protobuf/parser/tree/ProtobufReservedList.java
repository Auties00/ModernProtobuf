package it.auties.protobuf.parser.tree;

import java.util.stream.Collectors;

public final class ProtobufReservedList
        extends ProtobufMutableStatement
        implements ProtobufStatement, ProtobufTree.WithBody<ProtobufReserved>,
                   ProtobufMessageChild, ProtobufEnumChild, ProtobufGroupChild {
    private final ProtobufBody<ProtobufReserved> body;

    public ProtobufReservedList(int line) {
        super(line);
        this.body = new ProtobufBody<>(line);
    }

    @Override
    public ProtobufBody<ProtobufReserved> body() {
        return body;
    }

    @Override
    public boolean hasBody() {
        return body != null;
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

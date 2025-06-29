package it.auties.protobuf.parser.tree;

import java.util.Objects;

public final class ProtobufMessageStatement
        extends ProtobufStatementImpl
        implements ProtobufStatement, ProtobufTree.WithName, ProtobufTree.WithBody<ProtobufMessageChild>,
                   ProtobufDocumentChild, ProtobufGroupChild, ProtobufMessageChild {
    private String name;
    private ProtobufBody<ProtobufMessageChild> body;
    private final boolean extension;

    public ProtobufMessageStatement(int line, boolean extension) {
        super(line);
        this.extension = extension;
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();

        builder.append(extension ? "extend" : "message");
        builder.append(" ");

        var name = Objects.requireNonNullElse(this.name, "[missing]");
        builder.append(name);
        builder.append(" ");

        if(body != null) {
            builder.append("{");
            builder.append("\n");

            if(body.children().isEmpty()) {
                builder.append("\n");
            } else {
                body.children().forEach(statement -> {
                    builder.append("    ");
                    builder.append(statement);
                    builder.append("\n");
                });
            }

            builder.append("}");
        }

        return builder.toString();
    }

    @Override
    public boolean isAttributed() {
        return body.isAttributed();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean hasName() {
        return name != null;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public ProtobufBody<ProtobufMessageChild> body() {
        return body;
    }

    @Override
    public boolean hasBody() {
        return body != null;
    }

    public void setBody(ProtobufBody<ProtobufMessageChild> body) {
        if(body != null) {
            if(body.hasOwner()) {
                throw new IllegalStateException("Body is already owned by another tree");
            }
            body.setOwner(this);
        }
        this.body = body;
    }
}

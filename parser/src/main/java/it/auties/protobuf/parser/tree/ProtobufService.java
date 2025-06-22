package it.auties.protobuf.parser.tree;

import java.util.Objects;

public final class ProtobufService
        implements ProtobufStatement, ProtobufTree.WithName, ProtobufTree.WithBody<ProtobufServiceChild>,
                   ProtobufDocumentChild {
    private final int line;
    private String name;
    private ProtobufBody<ProtobufServiceChild> body;
    private ProtobufTree parent;

    public ProtobufService(int line) {
        this.line = line;
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

    @Override
    public String toString() {
        var builder = new StringBuilder();

        builder.append("service ");
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
    public ProtobufBody<ProtobufServiceChild> body() {
        return body;
    }

    @Override
    public boolean hasBody() {
        return body != null;
    }

    @Override
    public void setBody(ProtobufBody<ProtobufServiceChild> body) {
        if(body != null) {
            if(body.hasOwner()) {
                throw new IllegalStateException("Body is already owned by another tree");
            }
            body.setOwner(this);
        }
        this.body = body;
    }
}

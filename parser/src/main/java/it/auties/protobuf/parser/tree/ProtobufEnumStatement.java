package it.auties.protobuf.parser.tree;

import java.util.Objects;

public final class ProtobufEnumStatement
        extends ProtobufStatementImpl
        implements ProtobufStatement, ProtobufTree.WithName, ProtobufTree.WithBody<ProtobufEnumChild>,
                   ProtobufDocumentChild, ProtobufMessageChild, ProtobufGroupChild {
    private String name;
    private ProtobufBody<ProtobufEnumChild> body;

    public ProtobufEnumStatement(int line) {
        super(line);
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
    public ProtobufBody<ProtobufEnumChild> body() {
        return body;
    }

    @Override
    public boolean hasBody() {
        return body != null;
    }

    public void setBody(ProtobufBody<ProtobufEnumChild> body) {
        if(body != null) {
            if(body.hasOwner() && body.owner() != this) {
                throw new IllegalStateException("Body is already owned by another tree");
            }
            body.setOwner(this);
        }
        this.body = body;
    }

    @Override
    public boolean isAttributed() {
        return body.isAttributed();
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();

        builder.append("enum");
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
}

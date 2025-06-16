package it.auties.protobuf.parser.tree;

import java.util.Objects;

public final class ProtobufService
        extends ProtobufStatement
        implements ProtobufNamedTree,
                   ProtobufDocumentChild {
    private String name;
    private ProtobufTreeBody<ProtobufServiceChild> body;
    
    public ProtobufService(int line, ProtobufDocument parent) {
        super(line, parent.body());
        Objects.requireNonNull(parent, "parent cannot be null");
        parent.body()
                .addChild(this);
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

            body.children().forEach(statement -> {
                builder.append(statement);
                builder.append("\n");
            });

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

    public ProtobufTreeBody<ProtobufServiceChild> body() {
        return body;
    }

    public boolean hasBody() {
        return body != null;
    }

    public void setBody(ProtobufTreeBody<ProtobufServiceChild> body) {
        this.body = body;
    }
}

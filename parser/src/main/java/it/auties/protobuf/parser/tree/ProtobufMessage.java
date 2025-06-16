package it.auties.protobuf.parser.tree;

import java.util.Objects;

public final class ProtobufMessage
        extends ProtobufStatement
        implements ProtobufNamedTree,
                    ProtobufDocumentChild, ProtobufGroupChild, ProtobufMessageChild {
    private String name;
    private ProtobufTreeBody<ProtobufMessageChild> body;
    private final boolean extension;

    public ProtobufMessage(int line, boolean extension, ProtobufDocument parent) {
        super(line, parent.body());
        this.extension = extension;
        Objects.requireNonNull(parent, "parent cannot be null");
        parent.body()
                .addChild(this);
    }

    public ProtobufMessage(int line, boolean extension, ProtobufGroupField parent) {
        super(line, parent.body());
        this.extension = extension;
        Objects.requireNonNull(parent, "parent cannot be null");
        if(!parent.hasBody()) {
            throw new IllegalArgumentException("parent must have a body");
        }
        parent.body()
                .addChild(this);
    }

    public ProtobufMessage(int line, boolean extension, ProtobufMessage parent) {
        super(line, parent.body());
        this.extension = extension;
        Objects.requireNonNull(parent, "parent cannot be null");
        if(!parent.hasBody()) {
            throw new IllegalArgumentException("parent must have a body");
        }
        parent.body()
                .addChild(this);
    }

    public boolean isExtension() {
        return extension;
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();

        builder.append("message ");
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

    public ProtobufTreeBody<ProtobufMessageChild> body() {
        return body;
    }

    public boolean hasBody() {
        return body != null;
    }

    public void setBody(ProtobufTreeBody<ProtobufMessageChild> body) {
        this.body = body;
    }
}

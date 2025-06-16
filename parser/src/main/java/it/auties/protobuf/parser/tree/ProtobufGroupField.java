package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.type.ProtobufGroupType;
import it.auties.protobuf.parser.type.ProtobufTypeReference;

import java.util.Objects;

public final class ProtobufGroupField
        extends ProtobufField
        implements ProtobufMessageChild, ProtobufOneofChild, ProtobufGroupChild {
    private ProtobufTreeBody<ProtobufGroupChild> body;

    public ProtobufGroupField(int line, ProtobufGroupField parent) {
        super(line, parent.body());
        Objects.requireNonNull(parent, "parent cannot be null");
        if(!parent.hasBody()) {
            throw new IllegalArgumentException("parent must have a body");
        }
        parent.body()
                .addChild(this);
    }

    public ProtobufGroupField(int line, ProtobufMessage parent) {
        super(line, parent.body());
        Objects.requireNonNull(parent, "parent cannot be null");
        if(!parent.hasBody()) {
            throw new IllegalArgumentException("parent must have a body");
        }
        parent.body()
                .addChild(this);
    }

    public ProtobufGroupField(int line, ProtobufOneof parent) {
        super(line, parent.body());
        Objects.requireNonNull(parent, "parent cannot be null");
        if(!parent.hasBody()) {
            throw new IllegalArgumentException("parent must have a body");
        }
        parent.body()
                .addChild(this);
    }

    public ProtobufTreeBody<ProtobufGroupChild> body() {
        return body;
    }

    public boolean hasBody() {
        return body != null;
    }

    public void setBody(ProtobufTreeBody<ProtobufGroupChild> body) {
        this.body = body;
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();

        if(modifier != null && modifier.type() != Modifier.Type.NOTHING) {
            builder.append(modifier);
            builder.append(" ");
        }

        builder.append("group");
        builder.append(" ");

        var name = Objects.requireNonNullElse(this.name, "[missing]");
        builder.append(name);
        builder.append(" ");

        var index = Objects.requireNonNullElse(this.index, "[missing]");
        builder.append("=");
        builder.append(" ");
        builder.append(index);
        builder.append(" ");

        writeOptions(builder);

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
    public ProtobufTypeReference type() {
        return ProtobufGroupType.of(name, this);
    }

    @Override
    public void setType(ProtobufTypeReference type) {
        throw new UnsupportedOperationException("Cannot set the type of a group field");
    }
}

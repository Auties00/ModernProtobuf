package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.type.ProtobufGroupType;
import it.auties.protobuf.parser.type.ProtobufTypeReference;

import java.util.Objects;

public final class ProtobufGroupField
        extends ProtobufField
        implements ProtobufTree.WithBody<ProtobufGroupChild>,
                   ProtobufMessageChild, ProtobufOneofChild, ProtobufGroupChild {
    private ProtobufBody<ProtobufGroupChild> body;

    public ProtobufGroupField(int line) {
        super(line);
    }

    @Override
    public ProtobufBody<ProtobufGroupChild> body() {
        return body;
    }

    @Override
    public boolean hasBody() {
        return body != null;
    }

    public void setBody(ProtobufBody<ProtobufGroupChild> body) {
        if(body != null) {
            if(body.hasOwner()) {
                throw new IllegalStateException("Body is already owned by another tree");
            }
            body.setOwner(this);
        }
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

        // writeOptions(builder);

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
    public ProtobufTypeReference type() {
        return ProtobufGroupType.of(name, this);
    }

    @Override
    public void setType(ProtobufTypeReference type) {
        throw new UnsupportedOperationException("Cannot set the type of a group field");
    }
}

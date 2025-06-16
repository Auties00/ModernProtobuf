package it.auties.protobuf.parser.tree;

import java.util.*;
import java.util.stream.Collectors;

public final class ProtobufExtensionsList
        extends ProtobufStatement
        implements ProtobufMessageChild, ProtobufEnumChild, ProtobufGroupChild {
    private final ProtobufTreeBody<ProtobufExtension> body;

    public ProtobufExtensionsList(int line, ProtobufMessage parent) {
        super(line, parent.body());
        this.body = new ProtobufTreeBody<>(line, true, this);
        Objects.requireNonNull(parent, "parent cannot be null");
        if(!parent.hasBody()) {
            throw new IllegalArgumentException("parent must have a body");
        }
        parent.body()
                .addChild(this);
    }

    public ProtobufExtensionsList(int line, ProtobufEnum parent) {
        super(line, parent.body());
        this.body = new ProtobufTreeBody<>(line, true, this);
        Objects.requireNonNull(parent, "parent cannot be null");
        if(!parent.hasBody()) {
            throw new IllegalArgumentException("parent must have a body");
        }
        parent.body()
                .addChild(this);
    }

    public ProtobufExtensionsList(int line, ProtobufGroupField parent) {
        super(line, parent.body());
        this.body = new ProtobufTreeBody<>(line, true, this);
        Objects.requireNonNull(parent, "parent cannot be null");
        if(!parent.hasBody()) {
            throw new IllegalArgumentException("parent must have a body");
        }
        parent.body()
                .addChild(this);
    }

    public ProtobufTreeBody<ProtobufExtension> body() {
        return body;
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

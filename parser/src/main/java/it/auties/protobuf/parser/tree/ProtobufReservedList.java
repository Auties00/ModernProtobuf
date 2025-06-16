package it.auties.protobuf.parser.tree;

import java.util.*;
import java.util.stream.Collectors;

public final class ProtobufReservedList
        extends ProtobufStatement
        implements ProtobufMessageChild, ProtobufEnumChild, ProtobufGroupChild {
    private final ProtobufTreeBody<ProtobufReserved<?>> body;
  
    public ProtobufReservedList(int line, ProtobufMessage parent) {
        super(line, parent.body());
        this.body = new ProtobufTreeBody<>(line, true, this);
        Objects.requireNonNull(parent, "parent cannot be null");
        if(!parent.hasBody()) {
            throw new IllegalArgumentException("parent must have a body");
        }
        parent.body()
                .addChild(this);
    }

    public ProtobufReservedList(int line, ProtobufEnum parent) {
        super(line, parent.body());
        this.body = new ProtobufTreeBody<>(line, true, this);
        Objects.requireNonNull(parent, "parent cannot be null");
        if(!parent.hasBody()) {
            throw new IllegalArgumentException("parent must have a body");
        }
        parent.body()
                .addChild(this);
    }

    public ProtobufReservedList(int line, ProtobufGroupField parent) {
        super(line, parent.body());
        this.body = new ProtobufTreeBody<>(line, true, this);
        Objects.requireNonNull(parent, "parent cannot be null");
        if(!parent.hasBody()) {
            throw new IllegalArgumentException("parent must have a body");
        }
        parent.body()
                .addChild(this);
    }

    public ProtobufTreeBody<ProtobufReserved<?>> body() {
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

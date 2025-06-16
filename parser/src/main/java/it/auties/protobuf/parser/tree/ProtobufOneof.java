package it.auties.protobuf.parser.tree;

import java.util.Locale;
import java.util.Objects;

public final class ProtobufOneof
        extends ProtobufStatement
        implements ProtobufNamedTree,
        ProtobufMessageChild, ProtobufGroupChild {
    private String name;
    private ProtobufTreeBody<ProtobufOneofChild> body;
   
    public ProtobufOneof(int line, ProtobufMessage parent) {
        super(line, parent.body());
        Objects.requireNonNull(parent, "parent cannot be null");
        if(!parent.hasBody()) {
            throw new IllegalArgumentException("parent must have a body");
        }
        parent.body()
                .addChild(this);
    }

    public ProtobufOneof(int line, ProtobufGroupField parent) {
        super(line, parent.body());
        Objects.requireNonNull(parent, "parent cannot be null");
        if(!parent.hasBody()) {
            throw new IllegalArgumentException("parent must have a body");
        }
        parent.body()
                .addChild(this);
    }

    public String className() {
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1) + "Seal";
    }

    @Override
    public String toString() {
        return "oneof " + name + super.toString();
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

    public ProtobufTreeBody<ProtobufOneofChild> body() {
        return body;
    }

    public boolean hasBody() {
        return body != null;
    }

    public void setBody(ProtobufTreeBody<ProtobufOneofChild> body) {
        this.body = body;
    }
}

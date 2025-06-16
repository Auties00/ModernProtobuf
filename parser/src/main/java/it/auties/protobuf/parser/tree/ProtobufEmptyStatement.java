package it.auties.protobuf.parser.tree;

import java.util.Objects;

public final class ProtobufEmptyStatement
        extends ProtobufStatement
        implements ProtobufDocumentChild, ProtobufMessageChild, ProtobufEnumChild, ProtobufGroupChild, ProtobufOneofChild, ProtobufMethodChild, ProtobufServiceChild {
    public ProtobufEmptyStatement(int line, ProtobufDocument parent) {
        super(line, parent.body());
        Objects.requireNonNull(parent, "parent cannot be null");
        parent.body().addChild(this);
    }

    public ProtobufEmptyStatement(int line, ProtobufMessage parent) {
        super(line, parent.body());
        Objects.requireNonNull(parent, "parent cannot be null");
        if(!parent.hasBody()) {
            throw new IllegalArgumentException("parent must have a body");
        }
        parent.body()
                .addChild(this);
    }

    public ProtobufEmptyStatement(int line, ProtobufEnum parent) {
        super(line, parent.body());
        Objects.requireNonNull(parent, "parent cannot be null");
        if(!parent.hasBody()) {
            throw new IllegalArgumentException("parent must have a body");
        }
        parent.body()
                .addChild(this);
    }

    public ProtobufEmptyStatement(int line, ProtobufOneof parent) {
        super(line, parent.body());
        Objects.requireNonNull(parent, "parent cannot be null");
        if(!parent.hasBody()) {
            throw new IllegalArgumentException("parent must have a body");
        }
        parent.body()
                .addChild(this);
    }

    public ProtobufEmptyStatement(int line, ProtobufMethod parent) {
        super(line, parent.body());
        Objects.requireNonNull(parent, "parent cannot be null");
        if(!parent.hasBody()) {
            throw new IllegalArgumentException("parent must have a body");
        }
        parent.body()
                .addChild(this);
    }

    public ProtobufEmptyStatement(int line, ProtobufService parent) {
        super(line, parent.body());
        Objects.requireNonNull(parent, "parent cannot be null");
        if(!parent.hasBody()) {
            throw new IllegalArgumentException("parent must have a body");
        }
        parent.body()
                .addChild(this);
    }

    public ProtobufEmptyStatement(int line, ProtobufGroupField parent) {
        super(line, parent.body());
        Objects.requireNonNull(parent, "parent cannot be null");
        if(!parent.hasBody()) {
            throw new IllegalArgumentException("parent must have a body");
        }
        parent.body()
                .addChild(this);
    }

    @Override
    public boolean isAttributed() {
        return true;
    }

    @Override
    public String toString() {
        return ";";
    }
}

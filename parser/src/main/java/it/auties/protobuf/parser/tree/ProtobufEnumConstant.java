package it.auties.protobuf.parser.tree;

import java.util.Objects;

public final class ProtobufEnumConstant
        extends ProtobufField
        implements ProtobufEnumChild {
    public ProtobufEnumConstant(int line, ProtobufEnum parent) {
        super(line, parent.body());
        Objects.requireNonNull(parent, "parent cannot be null");
        if(!parent.hasBody()) {
            throw new IllegalArgumentException("parent must have a body");
        }
        parent.body()
                .addChild(this);
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();
        var name = Objects.requireNonNullElse(this.name, "[missing]");
        builder.append(name);
        builder.append(" = ");
        var index = Objects.requireNonNull(this.index, "[missing]");
        builder.append(index);
        writeOptions(builder);
        builder.append(";");
        return builder.toString();
    }
}

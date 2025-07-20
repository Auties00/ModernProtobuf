package it.auties.protobuf.parser.tree;

import java.util.Objects;

public final class ProtobufEnumConstantStatement
        extends ProtobufFieldStatement
        implements ProtobufEnumChild {
    public ProtobufEnumConstantStatement(int line) {
        super(line);
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

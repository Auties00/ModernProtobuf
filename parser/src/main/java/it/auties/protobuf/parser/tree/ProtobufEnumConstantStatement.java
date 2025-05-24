package it.auties.protobuf.parser.tree;

import java.util.Objects;

public final class ProtobufEnumConstantStatement
        extends ProtobufFieldStatement
        implements ProtobufEnumChildTree {
    public ProtobufEnumConstantStatement(int line, String name) {
        super(line);
        this.name = name;
    }

    @Override
    public String toString() {
        var name = Objects.requireNonNullElse(this.name, "[missing]");
        var index = Objects.requireNonNull(this.index, "[missing]");
        return toLeveledString(name + " = " + index + optionsToString());
    }
}

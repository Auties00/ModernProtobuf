package it.auties.protobuf.parser.tree;

import java.util.Objects;

public final class ProtobufOptionTree extends ProtobufNestedTree {
    private final String name;
    private String value;

    public ProtobufOptionTree(String name) {
        this.name = name;
    }

    @Override
    public boolean isAttributed() {
        return value != null;
    }

    public String name() {
        return name;
    }

    public String value() {
        return value;
    }

    public ProtobufOptionTree setValue(String value) {
        this.value = value;
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ProtobufOptionTree that
                && Objects.equals(this.name(), that.name())
                && Objects.equals(this.value(), that.value());
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }

    @Override
    public String toString() {
        var leading = parent instanceof ProtobufDocument ? "option " : "";
        var name = Objects.requireNonNullElse(this.name, "[missing]");
        var value = Objects.requireNonNullElse(this.value, "[missing]");
        var trailing = parent instanceof ProtobufDocument ? ";" : "";
        return "%s%s = %s%s".formatted(leading, name, value, trailing);
    }
}

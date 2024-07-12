package it.auties.protobuf.parser.tree;

import java.util.Objects;

public final class ProtobufOptionTree extends ProtobufNestedTree {
    private final String name;
    private Object value;
    private boolean attributed;

    public ProtobufOptionTree(String name) {
        this.name = name;
    }

    @Override
    public boolean isAttributed() {
        return attributed;
    }

    public boolean hasValue() {
        return value != null;
    }

    public String name() {
        return name;
    }

    public Object value() {
        return value;
    }

    public ProtobufOptionTree setRawValue(Object value) {
        this.value = value;
        this.attributed = false;
        return this;
    }

    public ProtobufOptionTree setAttributedValue(Object value) {
        this.value = value;
        this.attributed = true;
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
        var value = this.value instanceof String ? "\"%s\"".formatted(this.value) : Objects.requireNonNullElse(this.value, "[missing]");
        var trailing = parent instanceof ProtobufDocument ? ";" : "";
        return "%s%s = %s%s".formatted(leading, name, value, trailing);
    }
}

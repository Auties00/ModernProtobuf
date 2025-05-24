package it.auties.protobuf.parser.tree;

import java.util.Objects;
import java.util.Optional;

public final class ProtobufOptionStatement
        extends ProtobufStatement
        implements ProtobufDocumentChildTree, ProtobufMessageChildTree, ProtobufEnumChildTree, ProtobufOneofChildTree, ProtobufGroupChildTree {
    private final String name;
    private final ProtobufFieldStatement definition;
    private Object value;
    private boolean attributed;

    public ProtobufOptionStatement(int line, String name, ProtobufFieldStatement definition) {
        super(line);
        this.name = name;
        this.definition = definition;
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

    public Optional<ProtobufFieldStatement> definition() {
        return Optional.ofNullable(definition);
    }

    public Object value() {
        return value;
    }

    public ProtobufOptionStatement setRawValue(Object value) {
        this.value = value;
        this.attributed = false;
        return this;
    }

    public ProtobufOptionStatement setAttributedValue(Object value) {
        this.value = value;
        this.attributed = true;
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ProtobufOptionStatement that
                && Objects.equals(this.name(), that.name())
                && Objects.equals(this.value(), that.value());
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }

    @Override
    public String toString() {
        var leading = parent == null ? "" : "option ";
        var name = Objects.requireNonNullElse(this.name, "[missing]");
        var value = this.value instanceof String ? "\"%s\"".formatted(this.value) : Objects.requireNonNullElse(this.value, "[missing]");
        var trailing = parent instanceof ProtobufDocumentTree ? ";" : "";
        var result = "%s%s = %s%s".formatted(leading, name, value, trailing);
        return parent == null ? result : toLeveledString(result);
    }
}

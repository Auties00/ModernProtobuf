package it.auties.protobuf.parser.tree.nested.option;

import it.auties.protobuf.parser.tree.body.document.ProtobufDocumentTree;
import it.auties.protobuf.parser.tree.nested.ProtobufNestedTree;
import it.auties.protobuf.parser.tree.nested.field.ProtobufGroupableFieldTree;

import java.util.Objects;
import java.util.Optional;

public final class ProtobufOptionTree extends ProtobufNestedTree {
    private final String name;
    private final ProtobufGroupableFieldTree definition;
    private Object value;
    private boolean attributed;

    public ProtobufOptionTree(int line, String name, ProtobufGroupableFieldTree definition) {
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

    public Optional<ProtobufGroupableFieldTree> definition() {
        return Optional.ofNullable(definition);
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
        var leading = parent == null ? "" : "option ";
        var name = Objects.requireNonNullElse(this.name, "[missing]");
        var value = this.value instanceof String ? "\"%s\"".formatted(this.value) : Objects.requireNonNullElse(this.value, "[missing]");
        var trailing = parent instanceof ProtobufDocumentTree ? ";" : "";
        var result = "%s%s = %s%s".formatted(leading, name, value, trailing);
        return parent == null ? result : toLeveledString(result);
    }
}

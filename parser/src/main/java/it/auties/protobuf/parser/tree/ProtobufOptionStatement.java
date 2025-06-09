package it.auties.protobuf.parser.tree;

import java.util.Objects;
import java.util.Optional;

public final class ProtobufOptionStatement
        extends ProtobufStatement
        implements ProtobufDocumentChildTree, ProtobufMessageChildTree, ProtobufEnumChildTree, ProtobufOneofChildTree, ProtobufGroupChildTree {
    private String name;
    private ProtobufFieldStatement definition;
    private ProtobufOptionValue value;

    public ProtobufOptionStatement(int line) {
        super(line);
    }

    @Override
    public boolean isAttributed() {
        return hasName()
                && hasDefinition()
                && hasValue();
    }

    public String name() {
        return name;
    }

    public boolean hasName() {
        return name != null;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Optional<ProtobufFieldStatement> definition() {
        return Optional.ofNullable(definition);
    }

    public boolean hasDefinition() {
        return definition != null;
    }

    public void setDefinition(ProtobufFieldStatement definition) {
        this.definition = definition;
    }

    public Object value() {
        return value;
    }

    public boolean hasValue() {
        return value != null;
    }

    public void setValue(ProtobufOptionValue value) {
        this.value = value;
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
        var value = Objects.requireNonNullElse(this.value, "[missing]");
        var trailing = parent instanceof ProtobufDocumentTree ? ";" : "";
        var result = "%s%s = %s%s".formatted(leading, name, value, trailing);
        return parent == null ? result : toLeveledString(result);
    }
}

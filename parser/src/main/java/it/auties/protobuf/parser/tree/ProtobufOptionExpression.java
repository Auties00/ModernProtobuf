package it.auties.protobuf.parser.tree;

import java.util.Objects;
import java.util.Optional;

public final class ProtobufOptionExpression
        extends ProtobufExpressionImpl
        implements ProtobufExpression {
    private ProtobufOptionName name;
    private ProtobufExpression value;
    private ProtobufFieldStatement definition;

    public ProtobufOptionExpression(int line) {
        super(line);
    }

    @Override
    public boolean isAttributed() {
        return hasName()
               && hasDefinition()
               && hasValue();
    }

    public ProtobufOptionName name() {
        return name;
    }

    public boolean hasName() {
        return name != null;
    }

    public void setName(ProtobufOptionName name) {
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

    public ProtobufExpression value() {
        return value;
    }

    public boolean hasValue() {
        return value != null;
    }

    public void setValue(ProtobufExpression value) {
        if(value != null) {
            if(value.hasParent()) {
                throw new IllegalStateException("Value is already owned by another tree");
            }
            if(value instanceof ProtobufExpressionImpl impl) {
                impl.setParent(this);
            }
        }
        this.value = value;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ProtobufOptionExpression that
               && Objects.equals(this.name(), that.name())
               && Objects.equals(this.value(), that.value());
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();

        var name = Objects.requireNonNullElse(this.name, "[missing]");
        builder.append(name);

        builder.append(" ");
        builder.append("=");
        builder.append(" ");

        var value = Objects.requireNonNullElse(this.value, "[missing]");
        builder.append(value);

        return builder.toString();
    }
}

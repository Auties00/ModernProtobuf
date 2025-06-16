package it.auties.protobuf.parser.tree;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ProtobufOption
        extends ProtobufStatement
        implements ProtobufDocumentChild, ProtobufMessageChild, ProtobufEnumChild, ProtobufOneofChild, ProtobufServiceChild, ProtobufMethodChild {
    private String name;
    private ProtobufField definition;
    private Value value;

    public ProtobufOption(int line, ProtobufDocument parent) {
        super(line, parent.body());
        Objects.requireNonNull(parent, "parent cannot be null");
        parent.body()
                .addChild(this);
    }
    
    public ProtobufOption(int line, ProtobufMessage parent) {
        super(line, parent.body());
        Objects.requireNonNull(parent, "parent cannot be null");
        if(!parent.hasBody()) {
            throw new IllegalArgumentException("parent must have a body");
        }
        parent.body()
                .addChild(this);
    }

    public ProtobufOption(int line, ProtobufEnum parent) {
        super(line, parent.body());
        Objects.requireNonNull(parent, "parent cannot be null");
        if(!parent.hasBody()) {
            throw new IllegalArgumentException("parent must have a body");
        }
        parent.body()
                .addChild(this);
    }

    public ProtobufOption(int line, ProtobufOneof parent) {
        super(line, parent.body());
        Objects.requireNonNull(parent, "parent cannot be null");
        if(!parent.hasBody()) {
            throw new IllegalArgumentException("parent must have a body");
        }
        parent.body()
                .addChild(this);
    }

    public ProtobufOption(int line, ProtobufService parent) {
        super(line, parent.body());
        Objects.requireNonNull(parent, "parent cannot be null");
        if(!parent.hasBody()) {
            throw new IllegalArgumentException("parent must have a body");
        }
        parent.body()
                .addChild(this);
    }

    public ProtobufOption(int line, ProtobufMethod parent) {
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

    public Optional<ProtobufField> definition() {
        return Optional.ofNullable(definition);
    }

    public boolean hasDefinition() {
        return definition != null;
    }

    public void setDefinition(ProtobufField definition) {
        this.definition = definition;
    }

    public Object value() {
        return value;
    }

    public boolean hasValue() {
        return value != null;
    }

    public void setValue(Value value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ProtobufOption that
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

        builder.append("option");
        builder.append(" ");

        var name = Objects.requireNonNullElse(this.name, "[missing]");
        builder.append(name);

        builder.append(" ");
        builder.append("=");
        builder.append(" ");

        var value = Objects.requireNonNullElse(this.value, "[missing]");
        builder.append(value);

        builder.append(";");

        return builder.toString();
    }

    public sealed interface Value {
        record Literal(String value) implements Value {
            @Override
            public String toString() {
                return '"' + value + '"';
            }
        }

        record Int(int value) implements Value {
            @Override
            public String toString() {
                return String.valueOf(value);
            }
        }

        record Bool(boolean value) implements Value {
            @Override
            public String toString() {
                return String.valueOf(value);
            }
        }

        record Enum(String value) implements Value {
            @Override
            public String toString() {
                return String.valueOf(value);
            }
        }

        record Object(Map<String, Value> values) implements Value {
            @Override
            public Map<String, Value> values() {
                return Collections.unmodifiableMap(values);
            }

            // TODO: Stringify
            @Override
            public String toString() {
                return "todo";
            }
        }
    }
}

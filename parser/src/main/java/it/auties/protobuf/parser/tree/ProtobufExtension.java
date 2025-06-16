package it.auties.protobuf.parser.tree;

import java.util.Objects;

public final class ProtobufExtension extends ProtobufStatement {
    private Value value;

    public ProtobufExtension(int line, ProtobufExtensionsList parent) {
        super(line, parent.body());
        Objects.requireNonNull(parent, "parent cannot be null");
        parent.body()
                .addChild(this);
    }

    public Value value() {
        return value;
    }

    public boolean hasValue() {
        return value != null;
    }

    public void setValue(Value value) {
        this.value = value;
    }

    @Override
    public boolean isAttributed() {
        return hasValue();
    }

    public sealed interface Value {
        record FieldIndex(int value) implements Value {
            public boolean hasValue(Integer entry) {
                return entry != null && entry.equals(value);
            }

            @Override
            public String toString() {
                return String.valueOf(value);
            }
        }

        record FieldName(String value) implements Value {
            public boolean hasValue(String entry) {
                return value != null && value.equals(entry);
            }

            @Override
            public String toString() {
                return value;
            }
        }
    }
}

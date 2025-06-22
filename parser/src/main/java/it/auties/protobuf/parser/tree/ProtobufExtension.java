package it.auties.protobuf.parser.tree;

public final class ProtobufExtension
        implements ProtobufStatement {
    private final int line;
    private Value value;
    private ProtobufTree parent;

    public ProtobufExtension(int line) {
        this.line = line;
    }

    @Override
    public int line() {
        return line;
    }

    @Override
    public ProtobufTree parent() {
        return parent;
    }

    @Override
    public boolean hasParent() {
        return parent != null;
    }

    @Override
    public void setParent(ProtobufTree parent) {
        this.parent = parent;
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

        record FieldIndexRange(int min, int max) implements Value {
            public boolean hasValue(int entry) {
                return entry >= min
                       && entry <= max;
            }

            @Override
            public String toString() {
                return "%s to %s".formatted(min, max == Integer.MAX_VALUE ? "max" : max);
            }
        }
    }
}

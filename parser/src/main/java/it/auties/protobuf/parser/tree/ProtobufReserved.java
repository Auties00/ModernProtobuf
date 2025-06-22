package it.auties.protobuf.parser.tree;

public final class ProtobufReserved
        implements ProtobufStatement {
    private final int line;
    private Value value;
    private ProtobufTree parent;

    public ProtobufReserved(int line) {
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

        record FieldIndex(int value) implements Value {
            public boolean hasValue(int entry) {
                return value == entry;
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

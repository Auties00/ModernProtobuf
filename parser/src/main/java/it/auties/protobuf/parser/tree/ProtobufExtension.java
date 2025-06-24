package it.auties.protobuf.parser.tree;

public final class ProtobufExtension
        extends ProtobufMutableStatement
        implements ProtobufStatement {
    private Value value;

    public ProtobufExtension(int line) {
        super(line);
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

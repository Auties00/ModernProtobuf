package it.auties.protobuf.parser.tree;

public final class ProtobufReserved
        extends ProtobufMutableStatement
        implements ProtobufStatement {
    private Value value;

    public ProtobufReserved(int line) {
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

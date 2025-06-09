package it.auties.protobuf.parser.tree;

public sealed abstract class ProtobufReservedStatement<T> extends ProtobufStatement {
    protected ProtobufReservedStatement(int line) {
        super(line);
    }

    public abstract boolean hasValue(T value);

    public sealed abstract static class FieldIndex extends ProtobufReservedStatement<Integer> {
        protected FieldIndex(int line) {
            super(line);
        }

        public static final class Range extends FieldIndex {
            private Integer min;
            private Integer max;

            public Range(int line) {
                super(line);
            }

            public Integer min() {
                return min;
            }

            public boolean hasMin() {
                return min != null;
            }

            public void setMin(Integer min) {
                this.min = min;
            }

            public Integer max() {
                return max;
            }

            public boolean hasMax() {
                return max != null;
            }

            public void setMax(Integer max) {
                this.max = max;
            }

            @Override
            public boolean hasValue(Integer entry) {
                return entry != null
                        && min != null
                        && entry >= min
                        && max != null && entry <= max;
            }

            @Override
            public boolean isAttributed() {
                return hasMin() && hasMax();
            }

            @Override
            public String toString() {
                return "%s to %s".formatted(min, max != null && max == Integer.MAX_VALUE ? "max" : max);
            }
        }

        public static final class Value extends FieldIndex {
            private Integer value;

            public Value(int line) {
                super(line);
            }

            public Integer value() {
                return value;
            }

            public boolean hasValue() {
                return value != null;
            }

            public void setValue(Integer value) {
                this.value = value;
            }

            @Override
            public boolean hasValue(Integer entry) {
                return value != null && value.equals(entry);
            }

            @Override
            public boolean isAttributed() {
                return value != null;
            }

            @Override
            public String toString() {
                return value != null ? value.toString() : "<unknown>";
            }
        }
    }

    public static final class FieldName extends ProtobufReservedStatement<String> {
        private String value;

        public FieldName(int line) {
            super(line);
        }

        public String value() {
            return value;
        }

        public boolean hasValue() {
            return value != null;
        }

        public void setValue(String value) {
            this.value = value;
        }

        @Override
        public boolean hasValue(String entry) {
            return value != null && value.equals(entry);
        }

        @Override
        public boolean isAttributed() {
            return value != null;
        }

        @Override
        public String toString() {
            return value != null ? value : "<unknown>";
        }
    }
}

package it.auties.protobuf.parser.tree;

public sealed abstract class ProtobufExtensionStatement extends ProtobufStatement {
    protected ProtobufExtensionStatement(int line) {
        super(line);
    }

    public abstract boolean hasValue(int entry);

    public static final class Range extends ProtobufExtensionStatement {
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
        public boolean hasValue(int entry) {
            return min != null
                    && entry >= min
                    && max != null
                    && entry <= max;
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

    public static final class Value extends ProtobufExtensionStatement {
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
        public boolean isAttributed() {
            return value != null;
        }

        @Override
        public boolean hasValue(int entry) {
            return value != null && value == entry;
        }

        @Override
        public String toString() {
            return value == null ? "<unknown>" : value.toString();
        }
    }
}

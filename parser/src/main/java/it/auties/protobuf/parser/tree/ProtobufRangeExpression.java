package it.auties.protobuf.parser.tree;

public final class ProtobufRangeExpression
        extends ProtobufExpressionImpl
        implements ProtobufExpression, ProtobufExtensionsChild, ProtobufReservedChild {
    private Integer min;
    private Integer max;

    private ProtobufRangeExpression(int line) {
        super(line);
    }

    public Integer min() {
        return min;
    }

    public boolean hasMin() {
        return max != null;
    }

    public void setMin(Integer value) {
        this.min = value;
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

    public boolean hasValue(int entry) {
        return min != null
               && max != null
               && entry >= min
               && entry <= max;
    }

    @Override
    public boolean isAttributed() {
        return min != null && max != null;
    }

    @Override
    public String toString() {
        var min = this.min == null ? "[missing]" : this.min;
        var max = this.max == null ? "[missing]" : this.max == Integer.MAX_VALUE ? "max" : this.max;
        return "%s to %s".formatted(min, max);
    }
}

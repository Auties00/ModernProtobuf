package it.auties.protobuf.parser.tree;

public final class ProtobufFloatingPointExpression
        extends ProtobufExpressionImpl
        implements ProtobufNumberExpression {
    private Double value;

    public ProtobufFloatingPointExpression(int line) {
        super(line);
    }

    @Override
    public Double value() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    public boolean hasValue() {
        return value != null;
    }

    @Override
    public boolean isAttributed() {
        return value != null;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}

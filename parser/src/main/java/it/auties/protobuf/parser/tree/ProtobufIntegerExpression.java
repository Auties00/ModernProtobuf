package it.auties.protobuf.parser.tree;

public final class ProtobufIntegerExpression
        extends ProtobufExpressionImpl
        implements ProtobufExpression, ProtobufExtensionsChild, ProtobufReservedChild {
    private Integer value;
    public ProtobufIntegerExpression(int line) {
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
    public String toString() {
        return String.valueOf(value);
    }
}

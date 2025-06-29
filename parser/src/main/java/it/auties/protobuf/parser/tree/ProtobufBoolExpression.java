package it.auties.protobuf.parser.tree;

public final class ProtobufBoolExpression
        extends ProtobufExpressionImpl
        implements ProtobufExpression {
    private Boolean value;
    public ProtobufBoolExpression(int line) {
        super(line);
    }

    public Boolean value() {
        return value;
    }

    public boolean hasValue() {
        return value != null;
    }

    public void setValue(Boolean value) {
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

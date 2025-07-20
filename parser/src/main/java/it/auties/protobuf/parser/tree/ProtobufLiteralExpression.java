package it.auties.protobuf.parser.tree;

public final class ProtobufLiteralExpression
        extends ProtobufExpressionImpl
        implements ProtobufExpression, ProtobufReservedExpression {
    private String value;

    public ProtobufLiteralExpression(int line) {
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
    public boolean isAttributed() {
        return value != null;
    }

    @Override
    public String toString() {
        return "\"" + value + "\"";
    }
}

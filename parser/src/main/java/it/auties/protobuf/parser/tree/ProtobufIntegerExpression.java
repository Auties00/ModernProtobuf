package it.auties.protobuf.parser.tree;

public final class ProtobufIntegerExpression
        extends ProtobufExpressionImpl
        implements ProtobufNumberExpression, ProtobufExtensionsExpression, ProtobufReservedExpression {
    private Long value;

    public ProtobufIntegerExpression(int line) {
        super(line);
    }

    @Override
    public Long value() {
        return value;
    }

    public void setValue(Long value) {
        this.value = value;
    }

    public boolean hasValue() {
        return value != null;
    }

    @Override
    public boolean isAttributed() {
        return value != null;
    }
}

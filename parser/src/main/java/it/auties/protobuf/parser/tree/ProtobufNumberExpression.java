package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.type.ProtobufNumber;

public final class ProtobufNumberExpression
        extends ProtobufExpressionImpl
        implements ProtobufExtensionsExpression, ProtobufReservedExpression {
    private ProtobufNumber value;

    public ProtobufNumberExpression(int line) {
        super(line);
    }

    public ProtobufNumber value() {
        return value;
    }

    public void setValue(ProtobufNumber value) {
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

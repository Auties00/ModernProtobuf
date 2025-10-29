package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.type.ProtobufRange;

public final class ProtobufIntegerRangeExpression
        extends ProtobufExpressionImpl
        implements ProtobufExpression, ProtobufExtensionsExpression, ProtobufReservedExpression {
    private ProtobufRange value;

    public ProtobufIntegerRangeExpression(int line) {
        super(line);
    }

    public ProtobufRange value() {
        return value;
    }

    public void setValue(ProtobufRange value) {
        this.value = value;
    }

    @Override
    public boolean isAttributed() {
        return value != null;
    }

    @Override
    public String toString() {
        return value == null ? "[unknown]" : value.toString();
    }
}

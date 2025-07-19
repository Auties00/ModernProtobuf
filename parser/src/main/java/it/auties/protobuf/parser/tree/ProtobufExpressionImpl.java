package it.auties.protobuf.parser.tree;

sealed abstract class ProtobufExpressionImpl
        implements ProtobufExpression permits ProtobufBoolExpression, ProtobufEnumConstantExpression, ProtobufLiteralExpression, ProtobufMessageValueExpression, ProtobufNullExpression, ProtobufNumberExpression, ProtobufNumberExpression.Double, ProtobufFloatingPointExpression, ProtobufIntegerExpression, ProtobufIntegerExpression, ProtobufRangeExpression {
    final int line;
    ProtobufStatement parent;

    ProtobufExpressionImpl(int line) {
        this.line = line;
    }

    @Override
    public int line() {
        return line;
    }

    @Override
    public ProtobufStatement parent() {
        return parent;
    }

    @Override
    public boolean hasParent() {
        return parent != null;
    }

    void setParent(ProtobufStatement parent) {
        this.parent = parent;
    }
}

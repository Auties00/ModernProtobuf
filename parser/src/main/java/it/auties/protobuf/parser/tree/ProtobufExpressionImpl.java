package it.auties.protobuf.parser.tree;

sealed abstract class ProtobufExpressionImpl
        implements ProtobufExpression
        permits ProtobufBoolExpression, ProtobufEnumConstantExpression, ProtobufLiteralExpression, ProtobufMessageValueExpression, ProtobufNullExpression, ProtobufNumberExpression, ProtobufOptionExpression, ProtobufIntegerRangeExpression {
    final int line;
    ProtobufTree parent;

    ProtobufExpressionImpl(int line) {
        this.line = line;
    }

    @Override
    public int line() {
        return line;
    }

    @Override
    public ProtobufTree parent() {
        return parent;
    }

    @Override
    public boolean hasParent() {
        return parent != null;
    }

    void setParent(ProtobufTree parent) {
        this.parent = parent;
    }
}

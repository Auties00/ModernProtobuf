package it.auties.protobuf.parser.tree;

public final class ProtobufNullExpression
        extends ProtobufExpressionImpl
        implements ProtobufExpression {
    public ProtobufNullExpression(int line) {
        super(line);
    }

    @Override
    public boolean isAttributed() {
        return true;
    }

    @Override
    public String toString() {
        return "null";
    }
}

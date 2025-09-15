package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.type.ProtobufTypeReference;

public final class ProtobufEnumConstantExpression
        extends ProtobufExpressionImpl
        implements ProtobufExpression {
    private String name;
    private ProtobufTypeReference type;

    public ProtobufEnumConstantExpression(int line) {
        super(line);
    }

    public String name() {
        return name;
    }

    public boolean hasName() {
        return name != null;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ProtobufTypeReference type() {
        return type;
    }

    public boolean hasType() {
        return type != null;
    }

    public void setType(ProtobufTypeReference type) {
        this.type = type;
    }

    @Override
    public boolean isAttributed() {
        return name != null && type != null;
    }

    @Override
    public String toString() {
        return name;
    }
}

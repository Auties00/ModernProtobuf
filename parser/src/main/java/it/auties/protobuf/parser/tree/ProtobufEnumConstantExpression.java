package it.auties.protobuf.parser.tree;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.parser.type.ProtobufMessageOrEnumTypeReference;

public final class ProtobufEnumConstantExpression
        extends ProtobufExpressionImpl
        implements ProtobufExpression {
    private String name;
    private ProtobufMessageOrEnumTypeReference type;

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

    public ProtobufMessageOrEnumTypeReference type() {
        return type;
    }

    public boolean hasType() {
        return type != null;
    }

    public void setType(ProtobufMessageOrEnumTypeReference type) {
        if(type != null && type.hasDeclaration() && type.protobufType() != ProtobufType.ENUM) {
            throw new IllegalStateException("Type isn't an enum");
        }

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

package it.auties.protobuf.parser.statement;

public final class EnumConstantStatement extends ProtobufStatement {
    private final int index;

    public EnumConstantStatement(String name, int index) {
        super(name);
        this.index = index;
    }

    public int getIndex() {
        return index;
    }
}

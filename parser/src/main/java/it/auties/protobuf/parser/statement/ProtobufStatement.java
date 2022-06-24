package it.auties.protobuf.parser.statement;

import it.auties.protobuf.parser.object.ProtobufObject;

public sealed class ProtobufStatement permits ProtobufObject, EnumConstantStatement, FieldStatement {
    private final String name;

    public ProtobufStatement(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}

package it.auties.protobuf.parser.object;

import it.auties.protobuf.parser.statement.EnumStatement;
import it.auties.protobuf.parser.statement.MessageStatement;
import it.auties.protobuf.parser.statement.OneOfStatement;
import it.auties.protobuf.parser.statement.ProtobufStatement;

import java.util.List;

public sealed class ProtobufObject<T extends ProtobufStatement> extends ProtobufStatement
        permits ProtobufDocument, EnumStatement, MessageStatement, OneOfStatement {
    private final List<T> statements;

    public ProtobufObject(String name, List<T> statements) {
        super(name);
        this.statements = statements;
    }

    public List<T> getStatements() {
        return statements;
    }
}

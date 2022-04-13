package it.auties.protobuf.parser.object;

import it.auties.protobuf.parser.statement.EnumStatement;
import it.auties.protobuf.parser.statement.MessageStatement;
import it.auties.protobuf.parser.statement.OneOfStatement;
import it.auties.protobuf.parser.statement.ProtobufStatement;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@Data
public sealed class ProtobufObject<T extends ProtobufStatement> implements ProtobufStatement
        permits ProtobufDocument, EnumStatement, MessageStatement, OneOfStatement {
    private final String name;
    private final List<T> statements;

    public ProtobufObject(String name) {
        this(name, new ArrayList<>());
    }
}

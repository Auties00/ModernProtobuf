package it.auties.protobuf.parser.object;

import it.auties.protobuf.parser.statement.EnumStatement;
import it.auties.protobuf.parser.statement.MessageStatement;
import it.auties.protobuf.parser.statement.OneOfStatement;
import it.auties.protobuf.parser.statement.ProtobufStatement;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@EqualsAndHashCode(callSuper = true)
public abstract sealed class ProtobufObject<T extends ProtobufStatement> extends ProtobufStatement
        permits ProtobufDocument, EnumStatement, MessageStatement, OneOfStatement {
    private final List<T> statements;
    public ProtobufObject(){
        this(null, new LinkedList<>());
    }

    public ProtobufObject(String name, List<T> statements) {
        super(name);
        this.statements = statements;
    }

    @Override
    public String toString() {
        return toString(0);
    }
}

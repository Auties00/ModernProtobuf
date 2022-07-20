package it.auties.protobuf.parser.statement;

import it.auties.protobuf.parser.object.ProtobufObject;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;

@EqualsAndHashCode(callSuper = true)
public final class MessageStatement extends ProtobufObject<ProtobufStatement> {
    public MessageStatement(String name) {
        super(name, new ArrayList<>());
    }

    @Override
    public String toString() {
        return toString(0);
    }

    @Override
    public String toString(int level) {
        var builder = new StringBuilder()
                .append("%smessage".formatted(INDENTATION.repeat(level)))
                .append(" ")
                .append(name())
                .append(" ")
                .append("{")
                .append("\n");
        getStatements().forEach(statement -> {
            if (statement instanceof MessageStatement nestedMessage) {
                builder.append(nestedMessage.toString(level + 1));
            } else if (statement instanceof FieldStatement fieldStatement) {
                builder.append(fieldStatement.toString(level + 1));
            } else if (statement instanceof EnumStatement enumStatement) {
                builder.append(enumStatement.toString(level + 1));
            } else if (statement instanceof OneOfStatement oneOfStatement) {
                builder.append(oneOfStatement.toString(level + 1));
            } else {
                throw new UnsupportedOperationException(statement.getClass().getName());
            }
            builder.append("\n");
        });

        builder.append("%s}".formatted(INDENTATION.repeat(level)));
        return builder.toString();
    }
}

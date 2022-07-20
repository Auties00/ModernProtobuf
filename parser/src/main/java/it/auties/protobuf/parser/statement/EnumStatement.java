package it.auties.protobuf.parser.statement;

import it.auties.protobuf.parser.object.ProtobufObject;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;

@EqualsAndHashCode(callSuper = true)
public final class EnumStatement extends ProtobufObject<FieldStatement> {
    public EnumStatement(String name) {
        super(name);
    }

    @Override
    public String toString() {
        return toString(0);
    }

    @Override
    public String toString(int level){
        var builder = new StringBuilder()
                .append("%senum".formatted(INDENTATION.repeat(level)))
                .append(" ")
                .append(name())
                .append(" ")
                .append("{")
                .append("\n");
        getStatements().forEach(statement -> {
            builder.append(statement.toString(level + 1));
            builder.append("\n");
        });
        builder.append("%s}".formatted(INDENTATION.repeat(level)));
        return builder.toString();
    }
}

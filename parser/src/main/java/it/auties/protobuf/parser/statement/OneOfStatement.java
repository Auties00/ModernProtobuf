package it.auties.protobuf.parser.statement;

import it.auties.protobuf.parser.object.ProtobufObject;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.Locale;

@EqualsAndHashCode(callSuper = true)
public final class OneOfStatement extends ProtobufObject<FieldStatement> {
    public OneOfStatement(String name) {
        super(name, new ArrayList<>());
    }

    public String className() {
        return name().substring(0, 1).toUpperCase(Locale.ROOT)
                + name().substring(1);
    }

    @Override
    public String toString() {
        return toString(0);
    }

    @Override
    public String toString(int level){
        var builder = new StringBuilder()
                .append("%soneof".formatted(INDENTATION.repeat(level)))
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

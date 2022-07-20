package it.auties.protobuf.parser.statement;

import com.google.common.base.CaseFormat;
import it.auties.protobuf.parser.object.ProtobufObject;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
public final class OneOfStatement extends ProtobufObject<FieldStatement> {
    public OneOfStatement(String name) {
        super(name, new ArrayList<>());
    }

    public String getClassName() {
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, super.getName());
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
                .append(getName())
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

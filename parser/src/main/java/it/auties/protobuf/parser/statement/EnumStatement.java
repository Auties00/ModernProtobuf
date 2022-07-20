package it.auties.protobuf.parser.statement;

import it.auties.protobuf.parser.object.ProtobufObject;
import it.auties.protobuf.parser.object.ProtobufReservable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;

@Data
@Accessors(fluent = true)
@EqualsAndHashCode(callSuper = true)
public final class EnumStatement extends ProtobufObject<FieldStatement> implements ProtobufReservable {
    private final TreeSet<String> reservedNames;
    private final TreeSet<Integer> reservedIndexes;
    public EnumStatement(String name) {
        super(name);
        this.reservedNames = new TreeSet<>();
        this.reservedIndexes = new TreeSet<>();
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
        addReservedFields(level, builder);

        getStatements().forEach(statement -> {
            builder.append(statement.toString(level + 1));
            builder.append("\n");
        });
        builder.append("%s}".formatted(INDENTATION.repeat(level)));
        return builder.toString();
    }
}

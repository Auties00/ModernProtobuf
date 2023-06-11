package it.auties.protobuf.parser.statement;

public final class ProtobufEnumStatement extends ProtobufReservable<ProtobufFieldStatement> {
    public ProtobufEnumStatement(String name, String packageName, ProtobufObject<?>  parent) {
        super(name, packageName, parent);
    }

    @Override
    public String toString() {
        return toString(0);
    }

    @Override
    public ProtobufStatementType statementType() {
        return ProtobufStatementType.ENUM;
    }

    @Override
    public ProtobufObject<ProtobufFieldStatement> addStatement(ProtobufFieldStatement statement) {
        if(statement.type() != null){
            throw new IllegalArgumentException("Cannot add statement %s to an enum as it cannot have a protobufType".formatted(statement.type()));
        }

        return super.addStatement(statement);
    }

    String toString(int level){
        var builder = new StringBuilder()
                .append("%senum".formatted(INDENTATION.repeat(level)))
                .append(" ")
                .append(name())
                .append(" ")
                .append("{")
                .append("\n")
                .append(toPrettyReservable(level));

        statements().forEach(statement -> {
            builder.append(statement.toString(level + 1));
            builder.append("\n");
        });
        builder.append("%s}".formatted(INDENTATION.repeat(level)));
        return builder.toString();
    }
}

package it.auties.protobuf.parser.statement;

public final class ProtobufMessageStatement extends ProtobufReservable<ProtobufStatement> {
    public ProtobufMessageStatement(String name, String packageName, ProtobufObject<?> parent) {
        super(name, packageName, parent);
    }

    @Override
    public String toString() {
        return toString(0);
    }

    String toString(int level) {
        var builder = new StringBuilder()
                .append("%smessage".formatted(INDENTATION.repeat(level)))
                .append(" ")
                .append(name())
                .append(" ")
                .append("{")
                .append("\n")
                .append(toPrettyReservable(level));
        statements().forEach(statement -> {
            if (statement instanceof ProtobufMessageStatement nestedMessage) {
                builder.append(nestedMessage.toString(level + 1));
            } else if (statement instanceof ProtobufFieldStatement fieldStatement) {
                builder.append(fieldStatement.toString(level + 1));
            } else if (statement instanceof ProtobufEnumStatement enumStatement) {
                builder.append(enumStatement.toString(level + 1));
            } else if (statement instanceof ProtobufOneOfStatement oneOfStatement) {
                builder.append(oneOfStatement.toString(level + 1));
            } else {
                throw new UnsupportedOperationException(statement.getClass().getName());
            }
            builder.append("\n");
        });

        builder.append("%s}".formatted(INDENTATION.repeat(level)));
        return builder.toString();
    }

    @Override
    public ProtobufStatementType statementType() {
        return ProtobufStatementType.MESSAGE;
    }
}

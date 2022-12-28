package it.auties.protobuf.parser.statement;

import java.util.Locale;

public final class ProtobufOneOfStatement extends ProtobufObject<ProtobufFieldStatement> {
    public ProtobufOneOfStatement(String name, String packageName, ProtobufObject<?> parent) {
        super(name, packageName, parent);
    }

    public String className() {
        var className = name().substring(0, 1).toUpperCase(Locale.ROOT)
                + name().substring(1);
        return "%sType".formatted(className);
    }


    public String methodName() {
        return "%sType".formatted(name());
    }

    @Override
    public String toString() {
        return toString(0);
    }

    String toString(int level){
        var builder = new StringBuilder()
                .append("%soneof".formatted(INDENTATION.repeat(level)))
                .append(" ")
                .append(name())
                .append(" ")
                .append("{")
                .append("\n");
        statements().forEach(statement -> {
            builder.append(statement.toString(level + 1));
            builder.append("\n");
        });
        builder.append("%s}".formatted(INDENTATION.repeat(level)));
        return builder.toString();
    }

    @Override
    public ProtobufStatementType statementType() {
        return ProtobufStatementType.ONE_OF;
    }
}

package it.auties.protobuf.parser.tree;

import java.util.Objects;

public final class ProtobufEnumStatement
        extends ProtobufStatementWithBodyImpl<ProtobufEnumChild>
        implements ProtobufStatement, ProtobufTree.WithName, ProtobufTree.WithBody<ProtobufEnumChild>, ProtobufTree.WithBodyAndName<ProtobufEnumChild>,
                   ProtobufDocumentChild, ProtobufMessageChild, ProtobufGroupChild, ProtobufExtendChild {
    private String name;

    public ProtobufEnumStatement(int line) {
        super(line);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean hasName() {
        return name != null;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();

        builder.append("enum");
        builder.append(" ");

        var name = Objects.requireNonNullElse(this.name, "[missing]");
        builder.append(name);
        builder.append(" ");

        builder.append("{");
        builder.append("\n");

        if(children.isEmpty()) {
            builder.append("\n");
        } else {
            children.forEach(statement -> {
                builder.append("    ");
                builder.append(statement);
                builder.append("\n");
            });
        }

        builder.append("}");

        return builder.toString();
    }
}

package it.auties.protobuf.parser.tree;

import java.util.Objects;

public final class ProtobufMessageStatement
        extends ProtobufStatementWithBodyImpl<ProtobufMessageChild>
        implements ProtobufStatement, ProtobufTree.WithName, ProtobufTree.WithBody<ProtobufMessageChild>, ProtobufTree.WithBodyAndName<ProtobufMessageChild>,
                   ProtobufDocumentChild, ProtobufGroupChild, ProtobufMessageChild {
    private String name;
    private final boolean extension;

    public ProtobufMessageStatement(int line, boolean extension) {
        super(line);
        this.extension = extension;
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();

        builder.append(extension ? "extend" : "message");
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

    public boolean extension() {
        return extension;
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
}

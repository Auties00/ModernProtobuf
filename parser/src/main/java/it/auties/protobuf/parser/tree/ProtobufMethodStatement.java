package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.type.ProtobufTypeReference;

import java.util.Objects;

public final class ProtobufMethodStatement
        extends ProtobufStatementWithBodyImpl<ProtobufMethodChild>
        implements ProtobufStatement, ProtobufTree.WithBody<ProtobufMethodChild>,
                   ProtobufServiceChild, ProtobufTree.WithName {
    private String name;
    private Type inputType;
    private Type outputType;

    public ProtobufMethodStatement(int line) {
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

    public Type inputType() {
        return inputType;
    }

    public boolean hasInputType() {
        return inputType != null;
    }

    public void setInputType(Type inputType) {
        this.inputType = inputType;
    }

    public Type outputType() {
        return outputType;
    }

    public boolean hasOutputType() {
        return outputType != null;
    }

    public void setOutputType(Type outputType) {
        this.outputType = outputType;
    }

    @Override
    public boolean isAttributed() {
        return name != null && inputType != null && outputType != null;
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();

        builder.append("rpc ");

        var name = Objects.requireNonNullElse(this.name, "[missing]");
        builder.append(name);
        builder.append(" ");

        var inputType = Objects.requireNonNullElse(this.inputType, "[missing]");
        builder.append("(");
        builder.append(inputType);
        builder.append(") ");

        builder.append("returns ");

        var outputType = Objects.requireNonNullElse(this.outputType, "[missing]");
        builder.append("(");
        builder.append(outputType);
        builder.append(")");

        if (children.isEmpty()) {
            builder.append(";");
        } else {
            builder.append(" {");
            builder.append("\n");

            children.forEach(statement -> {
                builder.append("    ");
                builder.append(statement);
                builder.append("\n");
            });

            builder.append("};");
        }

        return builder.toString();
    }

    public record Type(ProtobufTypeReference value, boolean stream) {

    }
}
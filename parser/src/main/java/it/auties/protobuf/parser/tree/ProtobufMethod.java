package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.type.ProtobufMethodType;

import java.util.Objects;

public final class ProtobufMethod
        extends ProtobufMutableStatement
        implements ProtobufStatement, ProtobufTree.WithBody<ProtobufMethodChild>,
                   ProtobufServiceChild, ProtobufTree.WithName {
    private String name;
    private ProtobufMethodType inputType;
    private ProtobufMethodType outputType;
    private ProtobufBody<ProtobufMethodChild> body;

    public ProtobufMethod(int line) {
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
    public ProtobufBody<ProtobufMethodChild> body() {
        return body;
    }

    public void setBody(ProtobufBody<ProtobufMethodChild> body) {
        if(body != null) {
            if(body.hasOwner()) {
                throw new IllegalStateException("Body is already owned by another tree");
            }
            body.setOwner(this);
        }
        this.body = body;
    }

    @Override
    public boolean hasBody() {
        return body != null;
    }

    public ProtobufMethodType inputType() {
        return inputType;
    }

    public boolean hasInputType() {
        return inputType != null;
    }

    public void setInputType(ProtobufMethodType inputType) {
        this.inputType = inputType;
    }

    public ProtobufMethodType outputType() {
        return outputType;
    }

    public boolean hasOutputType() {
        return outputType != null;
    }

    public void setOutputType(ProtobufMethodType outputType) {
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

        if(body != null) {
            builder.append(" {");
            builder.append("\n");

            if(body.children().isEmpty()) {
                builder.append("\n");
            } else {
                body.children().forEach(statement -> {
                    builder.append("    ");
                    builder.append(statement);
                    builder.append("\n");
                });
            }

            builder.append("};");
        }else {
            builder.append(";");
        }

        return builder.toString();
    }
}
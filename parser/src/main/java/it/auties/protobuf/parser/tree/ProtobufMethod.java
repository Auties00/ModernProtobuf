package it.auties.protobuf.parser.tree;

import it.auties.protobuf.parser.type.ProtobufMethodType;

import java.util.Objects;

public final class ProtobufMethod
        extends ProtobufStatement
        implements ProtobufServiceChild, ProtobufNamedTree {
    private String name;
    private ProtobufMethodType inputType;
    private ProtobufMethodType outputType;
    private ProtobufTreeBody<ProtobufMethodChild> body;

    public ProtobufMethod(int line, ProtobufService parent) {
        super(line, parent.body());
        Objects.requireNonNull(parent, "parent cannot be null");
        if(!parent.hasBody()) {
            throw new IllegalArgumentException("parent must have a body");
        }
        parent.body()
                .addChild(this);
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

    public ProtobufTreeBody<ProtobufMethodChild> body() {
        return body;
    }

    public void setBody(ProtobufTreeBody<ProtobufMethodChild> body) {
        this.body = body;
    }

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

            body.children().forEach(statement -> {
                builder.append(statement);
                builder.append("\n");
            });

            builder.append("};");
        }else {
            builder.append(";");
        }

        return builder.toString();
    }
}
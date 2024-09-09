package it.auties.protobuf.serialization.generator.method;

import it.auties.protobuf.serialization.model.object.ProtobufObjectElement;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter;

import java.util.List;

public class ProtobufSerializationMethodOverloadGenerator extends ProtobufMethodGenerator {
    private static final String DEFAULT_PARAMETER_NAME = "protoInputObject";

    public ProtobufSerializationMethodOverloadGenerator(ProtobufObjectElement element) {
        super(element);
    }

    @Override
    protected void doInstrumentation(ClassWriter classWriter, ClassWriter.MethodWriter writer) {
        // Check if the input is null
        try(var ifWriter = writer.printIfStatement("%s == null".formatted(DEFAULT_PARAMETER_NAME))) {
            ifWriter.printReturn("null");
        }

        // Return the result
        writer.printVariableDeclaration("stream", "new ProtobufOutputStream(%s(%s))".formatted(ProtobufSizeMethodGenerator.METHOD_NAME, DEFAULT_PARAMETER_NAME));
        writer.println("encode(%s, stream);".formatted(DEFAULT_PARAMETER_NAME));
        writer.printReturn("stream.toByteArray()");
    }

    @Override
    public boolean shouldInstrument() {
        return !message.isEnum();
    }

    @Override
    protected List<String> modifiers() {
        return List.of("public", "static");
    }

    @Override
    protected String returnType() {
        return message.isEnum() ? "Integer" : "byte[]";
    }

    @Override
    public String name() {
        return "encode";
    }

    @Override
    protected List<String> parametersTypes() {
        return List.of(message.element().getSimpleName().toString());
    }

    @Override
    protected List<String> parametersNames() {
        return List.of(DEFAULT_PARAMETER_NAME);
    }
}

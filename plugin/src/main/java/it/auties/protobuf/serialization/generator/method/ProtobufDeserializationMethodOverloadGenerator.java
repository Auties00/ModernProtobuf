package it.auties.protobuf.serialization.generator.method;

import it.auties.protobuf.serialization.model.object.ProtobufObjectElement;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter;

import java.util.List;

public class ProtobufDeserializationMethodOverloadGenerator extends ProtobufMethodGenerator {
    public ProtobufDeserializationMethodOverloadGenerator(ProtobufObjectElement element) {
        super(element);
    }

    @Override
    protected void doInstrumentation(ClassWriter classWriter, ClassWriter.MethodWriter writer) {
        // Check if the input is null
        try(var ifWriter = writer.printIfStatement("input == null")) {
            ifWriter.printReturn("null");
        }

        // Return the result
        writer.printReturn("decode(new ProtobufInputStream(input, 0, input.length))");
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
    protected String name() {
        return "decode";
    }

    @Override
    protected String returnType() {
        return message.element().getSimpleName().toString();
    }

    @Override
    protected List<String> parametersTypes() {
        return List.of("byte[]");
    }

    @Override
    protected List<String> parametersNames() {
        return List.of("input");
    }
}

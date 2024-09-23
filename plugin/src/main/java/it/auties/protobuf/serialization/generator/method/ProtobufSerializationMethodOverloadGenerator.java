package it.auties.protobuf.serialization.generator.method;

import it.auties.protobuf.serialization.model.object.ProtobufObjectElement;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter;

import java.util.List;

public class ProtobufSerializationMethodOverloadGenerator extends ProtobufMethodGenerator {
    private static final String INPUT_OBJECT_PARAMETER = "protoInputObject";
    private static final String GROUP_INDEX_PARAMETER = "protoGroupIndex";

    public ProtobufSerializationMethodOverloadGenerator(ProtobufObjectElement element) {
        super(element);
    }

    @Override
    protected void doInstrumentation(ClassWriter classWriter, ClassWriter.MethodWriter writer) {
        // Check if the input is null
        try(var ifWriter = writer.printIfStatement("%s == null".formatted(INPUT_OBJECT_PARAMETER))) {
            ifWriter.printReturn("null");
        }

        // Return the result
        if(message.isGroup()) {
            writer.printVariableDeclaration("stream", "new ProtobufOutputStream(%s(%s, %s))".formatted(ProtobufSizeMethodGenerator.METHOD_NAME, GROUP_INDEX_PARAMETER, INPUT_OBJECT_PARAMETER));
            writer.println("encode(%s, %s, stream);".formatted(GROUP_INDEX_PARAMETER, INPUT_OBJECT_PARAMETER));
        }else {
            writer.printVariableDeclaration("stream", "new ProtobufOutputStream(%s(%s))".formatted(ProtobufSizeMethodGenerator.METHOD_NAME, INPUT_OBJECT_PARAMETER));
            writer.println("encode(%s, stream);".formatted(INPUT_OBJECT_PARAMETER));
        }

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
        var objectType = message.element().getSimpleName().toString();
        if(message.isGroup()) {
            return List.of("int", objectType);
        }else {
            return List.of(objectType);
        }
    }

    @Override
    protected List<String> parametersNames() {
        if(message.isGroup()) {
            return List.of(GROUP_INDEX_PARAMETER, INPUT_OBJECT_PARAMETER);
        }else {
            return List.of(INPUT_OBJECT_PARAMETER);
        }
    }
}

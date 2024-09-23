package it.auties.protobuf.serialization.generator.method;

import it.auties.protobuf.serialization.model.object.ProtobufObjectElement;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter;

import java.util.List;

public class ProtobufDeserializationMethodOverloadGenerator extends ProtobufMethodGenerator {
    private static final String INPUT_OBJECT_PARAMETER = "protoInputObject";
    private static final String GROUP_INDEX_PARAMETER = "protoGroupIndex";

    public ProtobufDeserializationMethodOverloadGenerator(ProtobufObjectElement element) {
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
            writer.printReturn("decode(%s, new ProtobufInputStream(%s, 0, %s.length))".formatted(GROUP_INDEX_PARAMETER, INPUT_OBJECT_PARAMETER, INPUT_OBJECT_PARAMETER));
        }else {
            writer.printReturn("decode(new ProtobufInputStream(%s, 0, %s.length))".formatted(INPUT_OBJECT_PARAMETER, INPUT_OBJECT_PARAMETER));
        }
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
        if(message.isGroup()) {
            return List.of("int", "byte[]");
        }else {
            return List.of("byte[]");
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

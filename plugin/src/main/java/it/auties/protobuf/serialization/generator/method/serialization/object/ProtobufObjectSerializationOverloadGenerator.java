package it.auties.protobuf.serialization.generator.method.serialization.object;

import it.auties.protobuf.serialization.generator.method.ProtobufMethodGenerator;
import it.auties.protobuf.serialization.model.object.ProtobufObjectElement;
import it.auties.protobuf.serialization.model.object.ProtobufObjectType;
import it.auties.protobuf.serialization.writer.ClassWriter;
import it.auties.protobuf.serialization.writer.MethodWriter;

import java.util.List;

public class ProtobufObjectSerializationOverloadGenerator extends ProtobufMethodGenerator {
    private static final String INPUT_OBJECT_PARAMETER = "protoInputObject";
    private static final String GROUP_INDEX_PARAMETER = "protoGroupIndex";

    public ProtobufObjectSerializationOverloadGenerator(ProtobufObjectElement element) {
        super(element);
    }

    @Override
    protected void doInstrumentation(ClassWriter classWriter, MethodWriter writer) {
        // Check if the input is null
        try(var ifWriter = writer.printIfStatement("%s == null".formatted(INPUT_OBJECT_PARAMETER))) {
            ifWriter.printReturn("null");
        }

        // Return the result
        if(objectElement.type() == ProtobufObjectType.GROUP) {
            writer.printVariableDeclaration("stream", "ProtobufOutputStream.toBytes(%s(%s, %s))".formatted(ProtobufObjectSizeGenerator.METHOD_NAME, GROUP_INDEX_PARAMETER, INPUT_OBJECT_PARAMETER));
            writer.println("encode(%s, %s, stream);".formatted(GROUP_INDEX_PARAMETER, INPUT_OBJECT_PARAMETER));
        }else {
            writer.printVariableDeclaration("stream", "ProtobufOutputStream.toBytes(%s(%s))".formatted(ProtobufObjectSizeGenerator.METHOD_NAME, INPUT_OBJECT_PARAMETER));
            writer.println("encode(%s, stream);".formatted(INPUT_OBJECT_PARAMETER));
        }

        writer.printReturn("stream.toOutput()");
    }

    @Override
    public boolean shouldInstrument() {
        return !(objectElement.type() == ProtobufObjectType.ENUM);
    }

    @Override
    protected List<String> modifiers() {
        return List.of("public", "static");
    }

    @Override
    protected String returnType() {
        return objectElement.type() == ProtobufObjectType.ENUM ? "Integer" : "byte[]";
    }

    @Override
    public String name() {
        return "encode";
    }

    @Override
    protected List<String> parametersTypes() {
        var objectType = objectElement.element().getSimpleName().toString();
        if(objectElement.type() == ProtobufObjectType.GROUP) {
            return List.of("int", objectType);
        }else {
            return List.of(objectType);
        }
    }

    @Override
    protected List<String> parametersNames() {
        if(objectElement.type() == ProtobufObjectType.GROUP) {
            return List.of(GROUP_INDEX_PARAMETER, INPUT_OBJECT_PARAMETER);
        }else {
            return List.of(INPUT_OBJECT_PARAMETER);
        }
    }
}

package it.auties.protobuf.serialization.generator.method.deserialization.object;

import it.auties.protobuf.serialization.generator.method.deserialization.ProtobufDeserializationGenerator;
import it.auties.protobuf.serialization.model.object.ProtobufObjectElement;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter;

import java.util.List;

public class ProtobufObjectDeserializationOverloadGenerator extends ProtobufDeserializationGenerator<ProtobufObjectElement> {
    private static final String INPUT_OBJECT_PARAMETER = "protoInputObject";
    private static final String ENUM_INDEX_PARAMETER = "protoEnumIndex";
    private static final String GROUP_INDEX_PARAMETER = "protoGroupIndex";

    public ProtobufObjectDeserializationOverloadGenerator(ProtobufObjectElement element) {
        super(element);
    }

    @Override
    protected void doInstrumentation(ClassWriter classWriter, ClassWriter.MethodWriter writer) {
        if(objectElement.isEnum()) {
            writer.printReturn("%s(%s, null)".formatted(name(), ENUM_INDEX_PARAMETER));
            return;
        }

        // Check if the input is null
        try(var ifWriter = writer.printIfStatement("%s == null".formatted(INPUT_OBJECT_PARAMETER))) {
            ifWriter.printReturn("null");
        }
        // Return the result
        if(objectElement.isGroup()) {
            writer.printReturn("%s(%s, ProtobufInputStream.fromBytes(%s, 0, %s.length))".formatted(name(), GROUP_INDEX_PARAMETER, INPUT_OBJECT_PARAMETER, INPUT_OBJECT_PARAMETER));
        }else {
            writer.printReturn("%s(ProtobufInputStream.fromBytes(%s, 0, %s.length))".formatted(name(), INPUT_OBJECT_PARAMETER, INPUT_OBJECT_PARAMETER));
        }
    }

    @Override
    public boolean shouldInstrument() {
        return true;
    }

    @Override
    protected String returnType() {
        return objectElement.element()
                .getSimpleName()
                .toString();
    }

    @Override
    protected List<String> parametersTypes() {
        if(objectElement.isGroup()) {
            return List.of("int", "byte[]");
        }else if(objectElement.isEnum()) {
            return List.of("int");
        }else {
            return List.of("byte[]");
        }
    }

    @Override
    protected List<String> parametersNames() {
        if(objectElement.isGroup()) {
            return List.of(GROUP_INDEX_PARAMETER, INPUT_OBJECT_PARAMETER);
        }else if(objectElement.isEnum()) {
            return List.of(ENUM_INDEX_PARAMETER);
        }else {
            return List.of(INPUT_OBJECT_PARAMETER);
        }
    }
}

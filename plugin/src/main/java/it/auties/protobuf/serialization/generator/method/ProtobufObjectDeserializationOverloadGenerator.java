package it.auties.protobuf.serialization.generator.method;

import it.auties.protobuf.serialization.model.ProtobufObjectElement;
import it.auties.protobuf.serialization.model.ProtobufObjectElement.Type;
import it.auties.protobuf.serialization.writer.ClassWriter;
import it.auties.protobuf.serialization.writer.MethodWriter;

import java.util.List;

public class ProtobufObjectDeserializationOverloadGenerator extends ProtobufDeserializationGenerator {
    private static final String INPUT_OBJECT_PARAMETER = "protoInputObject";
    private static final String ENUM_INDEX_PARAMETER = "protoEnumIndex";
    private static final String GROUP_INDEX_PARAMETER = "protoGroupIndex";

    public ProtobufObjectDeserializationOverloadGenerator(ProtobufObjectElement element) {
        super(element);
    }

    @Override
    protected void doInstrumentation(ClassWriter classWriter, MethodWriter writer) {
        if(objectElement.type() == Type.ENUM) {
            writer.printReturn("%s(%s, null)".formatted(name(), ENUM_INDEX_PARAMETER));
            return;
        }

        // Check if the input is null
        try(var ifWriter = writer.printIfStatement("%s == null".formatted(INPUT_OBJECT_PARAMETER))) {
            ifWriter.printReturn("null");
        }
        // Return the result
        if(objectElement.type() == Type.GROUP) {
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
        if(objectElement.type() == Type.GROUP) {
            return List.of("int", "byte[]");
        }else if(objectElement.type() == Type.ENUM) {
            return List.of("int");
        }else {
            return List.of("byte[]");
        }
    }

    @Override
    protected List<String> parametersNames() {
        return switch (objectElement.type()) {
            case GROUP -> List.of(GROUP_INDEX_PARAMETER, INPUT_OBJECT_PARAMETER);
            case ENUM -> List.of(ENUM_INDEX_PARAMETER);
            case MESSAGE -> List.of(INPUT_OBJECT_PARAMETER);
        };
    }
}

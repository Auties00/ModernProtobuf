package it.auties.protobuf.serialization.generator.method;

import it.auties.protobuf.serialization.model.ProtobufObjectElement;
import it.auties.protobuf.serialization.model.ProtobufObjectElement.Type;
import it.auties.protobuf.serialization.model.ProtobufPropertyType;
import it.auties.protobuf.serialization.writer.ClassWriter;
import it.auties.protobuf.serialization.writer.MethodWriter;

import javax.lang.model.element.Element;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

public class ProtobufObjectSizeGenerator extends ProtobufSizeGenerator {
    private static final String INPUT_OBJECT_PARAMETER = "protoInputObject";
    private static final String OUTPUT_SIZE_NAME = "protoOutputSize";
    private static final String GROUP_INDEX_PARAMETER = "protoGroupIndex";

    public ProtobufObjectSizeGenerator(ProtobufObjectElement element) {
        super(element);
    }

    @Override
    protected void doInstrumentation(ClassWriter classWriter, MethodWriter methodWriter) {
        try(var ifWriter = methodWriter.printIfStatement("%s == null".formatted(INPUT_OBJECT_PARAMETER))) {
            ifWriter.printReturn("0");
        }

        if(Objects.requireNonNull(objectElement).type() == Type.ENUM) {
            writeEnumCalculator(methodWriter);
        }else {
            writeMessageCalculator(classWriter, methodWriter);
        }
    }

    private void writeEnumCalculator(MethodWriter writer) {
        var metadata = Objects.requireNonNull(objectElement).enumMetadata()
                .orElseThrow(() -> new NoSuchElementException("Missing metadata from enum"));
        if(metadata.isJavaEnum()) {
            writer.printReturn("ProtobufOutputStream.getVarIntSize(%s.ordinal())".formatted(INPUT_OBJECT_PARAMETER));
        }else {
            var fieldName = metadata.field()
                    .getSimpleName();
            writer.printReturn("ProtobufOutputStream.getVarIntSize(%s.%s)".formatted(INPUT_OBJECT_PARAMETER, fieldName));
        }
    }

    private void writeMessageCalculator(ClassWriter classWriter, MethodWriter methodWriter) {
        methodWriter.printVariableDeclaration(OUTPUT_SIZE_NAME,"0");
        if(Objects.requireNonNull(objectElement).type() == Type.GROUP) {
            methodWriter.println("%s += ProtobufOutputStream.getFieldSize(%s, %s);".formatted(OUTPUT_SIZE_NAME, GROUP_INDEX_PARAMETER, "ProtobufWireType.WIRE_TYPE_START_OBJECT"));
            methodWriter.println("%s += ProtobufOutputStream.getFieldSize(%s, %s);".formatted(OUTPUT_SIZE_NAME, GROUP_INDEX_PARAMETER, "ProtobufWireType.WIRE_TYPE_END_OBJECT"));
        }

        for(var property : objectElement.properties()) {
            switch (property.type()) {
                case ProtobufPropertyType.CollectionType collectionType -> writeRepeatedSize(
                        methodWriter,
                        property.index(),
                        property.name(),
                        getAccessorCall(property.accessor()),
                        property.packed(),
                        collectionType,
                        false
                );
                case ProtobufPropertyType.MapType mapType -> writeMapSize(
                        classWriter,
                        methodWriter,
                        property.index(),
                        property.name(),
                        getAccessorCall(property.accessor()),
                        mapType,
                        false
                );
                case ProtobufPropertyType.NormalType ignored -> writeNormalSize(
                        methodWriter,
                        property
                );
            }
        }

        methodWriter.printReturn(OUTPUT_SIZE_NAME);
    }

    @Override
    protected List<String> parametersTypes() {
        var objectType = objectElement.element().getSimpleName().toString();
        if(objectElement.type() == Type.GROUP) {
            return List.of("int", objectType);
        }else {
            return List.of(objectType);
        }
    }

    @Override
    protected List<String> parametersNames() {
        if(objectElement.type() == Type.GROUP) {
            return List.of(GROUP_INDEX_PARAMETER, INPUT_OBJECT_PARAMETER);
        }else {
            return List.of(INPUT_OBJECT_PARAMETER);
        }
    }

    private String getAccessorCall(Element accessor) {
        return getAccessorCall(INPUT_OBJECT_PARAMETER, accessor);
    }
}

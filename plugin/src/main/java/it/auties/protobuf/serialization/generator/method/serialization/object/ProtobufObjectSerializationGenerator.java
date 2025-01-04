package it.auties.protobuf.serialization.generator.method.serialization.object;

import it.auties.protobuf.serialization.generator.method.serialization.ProtobufSerializationGenerator;
import it.auties.protobuf.serialization.model.object.ProtobufObjectElement;
import it.auties.protobuf.serialization.model.property.ProtobufPropertyElement;
import it.auties.protobuf.serialization.model.property.ProtobufPropertyType;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter.MethodWriter;
import it.auties.protobuf.stream.ProtobufOutputStream;

import javax.lang.model.element.Element;
import java.util.List;
import java.util.NoSuchElementException;

public class ProtobufObjectSerializationGenerator extends ProtobufSerializationGenerator {
    private static final String INPUT_OBJECT_PARAMETER = "protoInputObject";
    private static final String OUTPUT_OBJECT_PARAMETER = "protoOutputStream";
    private static final String GROUP_INDEX_PARAMETER = "protoGroupIndex";

    public ProtobufObjectSerializationGenerator(ProtobufObjectElement element) {
        super(element);
    }

    @Override
    protected void doInstrumentation(ClassWriter classWriter, MethodWriter writer) {
        if (objectElement.type() == ProtobufObjectElement.Type.ENUM) {
            createEnumSerializer(writer);
        } else {
            createMessageSerializer(writer);
        }
    }

    @Override
    public boolean shouldInstrument() {
        return true;
    }

    @Override
    protected List<String> modifiers() {
        return List.of("public", "static");
    }

    @Override
    protected String returnType() {
        return objectElement.type() == ProtobufObjectElement.Type.ENUM ? "Integer" : "void";
    }

    @Override
    protected List<String> parametersTypes() {
        var objectType = objectElement.element().getSimpleName().toString();
        if (objectElement.type() == ProtobufObjectElement.Type.ENUM) {
            return List.of(objectType);
        }else if(objectElement.type() == ProtobufObjectElement.Type.GROUP) {
            return List.of("int", objectType, ProtobufOutputStream.class.getSimpleName());
        }else {
            return List.of(objectType, ProtobufOutputStream.class.getSimpleName());
        }
    }

    @Override
    protected List<String> parametersNames() {
        if (objectElement.type() == ProtobufObjectElement.Type.ENUM) {
            return List.of(INPUT_OBJECT_PARAMETER);
        }else if(objectElement.type() == ProtobufObjectElement.Type.GROUP) {
            return List.of(GROUP_INDEX_PARAMETER, INPUT_OBJECT_PARAMETER, OUTPUT_OBJECT_PARAMETER);
        }else {
            return List.of(INPUT_OBJECT_PARAMETER, OUTPUT_OBJECT_PARAMETER);
        }
    }

    private void createEnumSerializer(MethodWriter writer) {
        try(var ifWriter = writer.printIfStatement("%s == null".formatted(INPUT_OBJECT_PARAMETER))) {
            ifWriter.printReturn("null");
        }

        var metadata = objectElement.enumMetadata()
                .orElseThrow(() -> new NoSuchElementException("Missing metadata from enum"));
        if(metadata.isJavaEnum()) {
            writer.printReturn("%s.ordinal()".formatted(INPUT_OBJECT_PARAMETER));
        }else {
            var fieldName = metadata.field().getSimpleName();
            writer.printReturn("%s.%s".formatted(INPUT_OBJECT_PARAMETER, fieldName));
        }
    }

    private void createMessageSerializer(MethodWriter writer) {
        try(var ifWriter = writer.printIfStatement("%s == null".formatted(INPUT_OBJECT_PARAMETER))) {
            ifWriter.printReturn();
        }

        if(objectElement.type() == ProtobufObjectElement.Type.GROUP) {
            writer.println("%s.writeGroupStart(%s);".formatted(OUTPUT_OBJECT_PARAMETER, GROUP_INDEX_PARAMETER));
        }

        createRequiredPropertiesNullCheck(writer);
        for(var property : objectElement.properties()) {
            switch (property.type()) {
                case ProtobufPropertyType.CollectionType collectionType -> writeRepeatedSerializer(writer, property.index(), property.name(), getAccessorCall(property.accessor()), collectionType, property.packed(), true, false);
                case ProtobufPropertyType.MapType mapType -> writeMapSerializer(writer, property.index(), property.name(), getAccessorCall(property.accessor()), mapType, true, false);
                default -> writeNormalSerializer(writer, property.index(), property.name(), getAccessorCall(property.accessor()), property.type(), true, true, false);
            }
        }

        if(objectElement.type() == ProtobufObjectElement.Type.GROUP) {
            writer.println("%s.writeGroupEnd(%s);".formatted(OUTPUT_OBJECT_PARAMETER, GROUP_INDEX_PARAMETER));
        }
    }

    private void createRequiredPropertiesNullCheck(MethodWriter writer) {
        objectElement.properties()
                .stream()
                .filter(ProtobufPropertyElement::required)
                .forEach(entry -> writer.println("Objects.requireNonNull(%s, \"Missing required property: %s\");".formatted(getAccessorCall(entry.accessor()), entry.name())));
    }

    private String getAccessorCall(Element accessor) {
        return getAccessorCall(INPUT_OBJECT_PARAMETER, accessor);
    }
}

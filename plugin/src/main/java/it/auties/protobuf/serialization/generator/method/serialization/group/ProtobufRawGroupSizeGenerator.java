package it.auties.protobuf.serialization.generator.method.serialization.group;

import it.auties.protobuf.exception.ProtobufSerializationException;
import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.model.ProtobufWireType;
import it.auties.protobuf.serialization.generator.method.serialization.ProtobufSizeGenerator;
import it.auties.protobuf.serialization.model.converter.ProtobufAttributedConverterElement;
import it.auties.protobuf.serialization.model.property.ProtobufPropertyType;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter.MethodWriter;

import javax.lang.model.element.TypeElement;
import java.util.List;

// SPECIAL CASE: raw groups
public class ProtobufRawGroupSizeGenerator extends ProtobufSizeGenerator<TypeElement> {
    private static final String OUTPUT_SIZE_NAME = "protoOutputSize";
    private static final String INDEX_PARAMETER = "protoGroupIndex";
    private static final String PROPERTIES_PARAMETER = "protoGroupProperties";

    private static final String PROTO_GROUP_ENTRY = "protoGroupEntry";
    private static final String PROTO_GROUP_ENTRY_VALUE = "protoGroupEntryValue";
    private static final String PROTO_GROUP_PROPERTY = "protoGroupProperty";

    private final ProtobufAttributedConverterElement.Serializer serializerElement;
    public ProtobufRawGroupSizeGenerator(TypeElement objectElement, ProtobufAttributedConverterElement.Serializer serializerElement) {
        super(objectElement);
        this.serializerElement = serializerElement;
    }

    @Override
    protected void doInstrumentation(ClassWriter classWriter, MethodWriter methodWriter) {
        methodWriter.printVariableDeclaration(OUTPUT_SIZE_NAME, "0");
        methodWriter.println("%s += ProtobufOutputStream.getFieldSize(%s, %s);".formatted(OUTPUT_SIZE_NAME, INDEX_PARAMETER, ProtobufWireType.WIRE_TYPE_START_OBJECT));
        methodWriter.println("%s += ProtobufOutputStream.getFieldSize(%s, %s);".formatted(OUTPUT_SIZE_NAME, INDEX_PARAMETER, ProtobufWireType.WIRE_TYPE_END_OBJECT));
        try (var forEachBody = methodWriter.printForEachStatement(PROTO_GROUP_ENTRY, "%s.entrySet()".formatted(PROPERTIES_PARAMETER))) {
            try (var switchBody = forEachBody.printSwitchStatement(PROTO_GROUP_ENTRY + ".getKey()")) {
                for (var entry : serializerElement.groupProperties().entrySet()) {
                    try (var switchCaseBody = switchBody.printSwitchBranch(String.valueOf(entry.getKey()))) {
                        var cast = entry.getValue().type().protobufType() == ProtobufType.GROUP || entry.getValue().type().protobufType() == ProtobufType.MESSAGE ||  entry.getValue().type().protobufType() == ProtobufType.ENUM;
                        var propertyValueName = switchCaseBody.printVariableDeclaration(PROTO_GROUP_ENTRY_VALUE, "%s%s.getValue()".formatted(cast ? "(%s) ".formatted(entry.getValue().type().descriptorElementType()) : "", PROTO_GROUP_ENTRY));
                        switch (entry.getValue().type()) {
                            case ProtobufPropertyType.CollectionType collectionType -> {
                                var collectionField = "((java.util.Collection) %s)".formatted(propertyValueName);
                                writeRepeatedSize(
                                        switchCaseBody,
                                        entry.getKey(),
                                        propertyValueName,
                                        collectionField,
                                        entry.getValue().packed(),
                                        collectionType,
                                        true
                                );
                            }
                            case ProtobufPropertyType.MapType mapType -> {
                                var mapField = "((java.util.Map) %s)".formatted(propertyValueName);
                                writeMapSize(
                                        classWriter,
                                        switchCaseBody,
                                        entry.getKey(),
                                        PROTO_GROUP_ENTRY + entry.getKey(),
                                        mapField, mapType,
                                        true
                                );
                            }
                            case ProtobufPropertyType.NormalType normalType -> writeNormalSize(
                                    switchCaseBody,
                                    entry.getKey(),
                                    PROTO_GROUP_PROPERTY,
                                    normalType,
                                    normalType.serializedType(),
                                    propertyValueName
                            );
                        }
                    }
                }
                switchBody.printSwitchBranch("default", "throw %s.unknownRawGroupFieldDefinition(%s)".formatted(ProtobufSerializationException.class.getName(), PROTO_GROUP_ENTRY + ".getKey()"));
            }
        }
        methodWriter.printReturn(OUTPUT_SIZE_NAME);
    }

    @Override
    protected List<String> parametersTypes() {
        return List.of("int", "Map<Integer, Object>");
    }

    @Override
    protected List<String> parametersNames() {
        return List.of(INDEX_PARAMETER, PROPERTIES_PARAMETER);
    }
}

package it.auties.protobuf.serialization.generator.method.serialization.group;

import it.auties.protobuf.exception.ProtobufSerializationException;
import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.model.ProtobufWireType;
import it.auties.protobuf.serialization.generator.method.serialization.ProtobufSizeGenerator;
import it.auties.protobuf.serialization.model.object.ProtobufObjectElement;
import it.auties.protobuf.serialization.model.property.ProtobufPropertyElement;
import it.auties.protobuf.serialization.model.property.ProtobufPropertyType;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter.MethodWriter;

import java.util.List;

// SPECIAL CASE: raw groups
public class ProtobufRawGroupSizeGenerator extends ProtobufSizeGenerator {
    private static final String OUTPUT_SIZE_NAME = "protoOutputSize";
    private static final String INDEX_PARAMETER = "protoGroupIndex";
    private static final String PROPERTIES_PARAMETER = "protoGroupProperties";

    private static final String PROTO_GROUP_ENTRY = "protoGroupEntry";
    private static final String PROTO_GROUP_ENTRY_VALUE = "protoGroupEntryValue";
    private static final String PROTO_GROUP_PROPERTY = "protoGroupProperty";

    public ProtobufRawGroupSizeGenerator(ProtobufObjectElement element) {
        super(element);
    }


    @Override
    protected void doInstrumentation(ClassWriter classWriter, MethodWriter methodWriter) {
        methodWriter.printVariableDeclaration(OUTPUT_SIZE_NAME, "0");
        methodWriter.println("%s += ProtobufOutputStream.getFieldSize(%s, %s);".formatted(OUTPUT_SIZE_NAME, INDEX_PARAMETER, ProtobufWireType.WIRE_TYPE_START_OBJECT));
        methodWriter.println("%s += ProtobufOutputStream.getFieldSize(%s, %s);".formatted(OUTPUT_SIZE_NAME, INDEX_PARAMETER, ProtobufWireType.WIRE_TYPE_END_OBJECT));
        try (var forEachBody = methodWriter.printForEachStatement(PROTO_GROUP_ENTRY, "%s.entrySet()".formatted(PROPERTIES_PARAMETER))) {
            try (var switchBody = forEachBody.printSwitchStatement(PROTO_GROUP_ENTRY + ".getKey()")) {
                for (var entry : objectElement.properties()) {
                    try (var switchCaseBody = switchBody.printSwitchBranch(String.valueOf(entry.index()))) {
                        switch (entry.type()) {
                            case ProtobufPropertyType.CollectionType collectionType -> {
                                var propertyValueName = switchCaseBody.printVariableDeclaration(
                                        PROTO_GROUP_ENTRY_VALUE,
                                        "(java.util.Collection) %s.getValue()".formatted(PROTO_GROUP_ENTRY)
                                );
                                writeRepeatedSize(
                                        switchCaseBody,
                                        entry.index(),
                                        PROTO_GROUP_ENTRY + entry.index(),
                                        propertyValueName,
                                        entry.packed(),
                                        collectionType,
                                        true
                                );
                            }
                            case ProtobufPropertyType.MapType mapType -> {
                                var propertyValueName = switchCaseBody.printVariableDeclaration(
                                        PROTO_GROUP_ENTRY_VALUE,
                                        "(java.util.Map) %s.getValue()".formatted(PROTO_GROUP_ENTRY)
                                );
                                writeMapSize(
                                        classWriter,
                                        switchCaseBody,
                                        entry.index(),
                                        PROTO_GROUP_ENTRY + entry.index(),
                                        propertyValueName,
                                        mapType,
                                        true
                                );
                            }
                            case ProtobufPropertyType.NormalType normalType -> {
                                var propertyValueName = switchCaseBody.printVariableDeclaration(
                                        PROTO_GROUP_ENTRY_VALUE,
                                        "%s%s.getValue()".formatted(
                                                isObject(entry) ? "(%s) ".formatted(entry.type().descriptorElementType()) : "",
                                                PROTO_GROUP_ENTRY
                                        )
                                );
                                writeNormalSize(
                                        switchCaseBody,
                                        entry.index(),
                                        PROTO_GROUP_PROPERTY,
                                        normalType,
                                        normalType.serializedType(),
                                        propertyValueName
                                );
                            }
                        }
                    }
                }
                switchBody.printSwitchBranch("default", "throw %s.unknownRawGroupFieldDefinition(%s)".formatted(ProtobufSerializationException.class.getName(), PROTO_GROUP_ENTRY + ".getKey()"));
            }
        }
        methodWriter.printReturn(OUTPUT_SIZE_NAME);
    }

    private static boolean isObject(ProtobufPropertyElement entry) {
        return entry.type().protobufType() == ProtobufType.GROUP
                || entry.type().protobufType() == ProtobufType.MESSAGE || entry.type().protobufType() == ProtobufType.ENUM;
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

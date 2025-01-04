package it.auties.protobuf.serialization.generator.method.deserialization.group;

import it.auties.protobuf.serialization.generator.method.deserialization.ProtobufDeserializationGenerator;
import it.auties.protobuf.serialization.model.object.ProtobufObjectElement;
import it.auties.protobuf.serialization.model.property.ProtobufPropertyType;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter.MethodWriter;

import java.util.List;

// SPECIAL CASE: raw groups
public class ProtobufRawGroupDeserializationGenerator extends ProtobufDeserializationGenerator {
    private static final String INDEX_PARAMETER = "protoGroupIndex";
    private static final String INPUT_OBJECT_PARAMETER = "protoInputStream";

    public ProtobufRawGroupDeserializationGenerator(ProtobufObjectElement element) {
        super(element);
    }

    @Override
    protected void doInstrumentation(ClassWriter classWriter, MethodWriter methodWriter) {
        methodWriter.println("%s.assertGroupOpened(%s);".formatted(INPUT_OBJECT_PARAMETER, INDEX_PARAMETER));
        var rawGroupData = methodWriter.printVariableDeclaration("groupData", "new java.util.HashMap()");
        for(var property : objectElement.properties()) {
            switch (property.type()) {
                case ProtobufPropertyType.CollectionType collectionType -> {
                    var collection = methodWriter.printVariableDeclaration("property" + property.index(), collectionType.descriptorDefaultValue());
                    methodWriter.printf("%s.put(%s, %s);".formatted(rawGroupData, property.index(), collection));
                }
                case ProtobufPropertyType.MapType mapType -> {
                    var map = methodWriter.printVariableDeclaration("property" + property.index(), mapType.descriptorDefaultValue());
                    methodWriter.printf("%s.put(%s, %s);".formatted(rawGroupData, property.index(), map));
                }
                case ProtobufPropertyType.NormalType ignored -> {}
            }
        }

        try(var whileWriter = methodWriter.printWhileStatement(INPUT_OBJECT_PARAMETER + ".readTag()")) {
            var index = whileWriter.printVariableDeclaration("index", INPUT_OBJECT_PARAMETER + ".index()");
            try(var mapSwitchWriter = whileWriter.printSwitchStatement(index)) {
                for(var groupProperty : objectElement.properties()) {
                    var groupPropertyIndex = groupProperty.index();
                    switch (groupProperty.type()) {
                        case ProtobufPropertyType.MapType mapType -> writeMapDeserializer(
                                mapSwitchWriter,
                                groupPropertyIndex,
                                "property" + groupProperty.index(),
                                mapType
                        );
                        case ProtobufPropertyType.CollectionType collectionType -> writeDeserializer(
                                mapSwitchWriter,
                                "property" + groupProperty.index(),
                                groupPropertyIndex,
                                collectionType.valueType(),
                                true,
                                groupProperty.packed(),
                                rawGroupData
                        );
                        default -> writeDeserializer(
                                mapSwitchWriter,
                                rawGroupData,
                                groupPropertyIndex,
                                groupProperty.type(),
                                false,
                                groupProperty.packed(),
                                rawGroupData
                        );
                    }
                }
            }
        }
        methodWriter.println("%s.assertGroupClosed(%s);".formatted(INPUT_OBJECT_PARAMETER, INDEX_PARAMETER));
        methodWriter.printReturn(rawGroupData);
    }

    @Override
    public boolean shouldInstrument() {
        return true;
    }

    @Override
    protected String returnType() {
        return "java.util.Map<Integer, Object>";
    }

    @Override
    protected List<String> parametersTypes() {
        return List.of("int", "ProtobufInputStream");
    }

    @Override
    protected List<String> parametersNames() {
        return List.of(INDEX_PARAMETER, INPUT_OBJECT_PARAMETER);
    }
}

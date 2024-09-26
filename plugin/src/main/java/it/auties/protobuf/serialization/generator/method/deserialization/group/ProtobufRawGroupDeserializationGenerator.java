package it.auties.protobuf.serialization.generator.method.deserialization.group;

import it.auties.protobuf.serialization.generator.method.deserialization.ProtobufDeserializationGenerator;
import it.auties.protobuf.serialization.model.converter.ProtobufSerializerElement;
import it.auties.protobuf.serialization.model.property.ProtobufGroupPropertyElement;
import it.auties.protobuf.serialization.model.property.ProtobufPropertyType;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter.MethodWriter;

import javax.lang.model.element.TypeElement;
import java.util.List;
import java.util.Map;

// SPECIAL CASE: raw groups
public class ProtobufRawGroupDeserializationGenerator extends ProtobufDeserializationGenerator<TypeElement> {
    private static final String INDEX_PARAMETER = "protoGroupIndex";
    private static final String INPUT_OBJECT_PARAMETER = "protoInputStream";

    private final ProtobufSerializerElement serializerElement;
    public ProtobufRawGroupDeserializationGenerator(TypeElement objectElement, ProtobufSerializerElement serializerElement) {
        super(objectElement);
        this.serializerElement = serializerElement;
    }

    @Override
    protected void doInstrumentation(ClassWriter classWriter, MethodWriter methodWriter) {
        methodWriter.println("%s.assertGroupOpened(%s);".formatted(INPUT_OBJECT_PARAMETER, INDEX_PARAMETER));
        var rawGroupData = methodWriter.printVariableDeclaration("groupData", "new java.util.HashMap()");
        for(var property : serializerElement.groupProperties().entrySet()) {
            if(property.getValue().type() instanceof ProtobufPropertyType.CollectionType collectionType) {
                methodWriter.printVariableDeclaration(getRawGroupCollectionFieldName(property), collectionType.descriptorDefaultValue());
            }
        }

        try(var whileWriter = methodWriter.printWhileStatement(INPUT_OBJECT_PARAMETER + ".readTag()")) {
            var index = whileWriter.printVariableDeclaration("index", INPUT_OBJECT_PARAMETER + ".index()");
            try(var mapSwitchWriter = whileWriter.printSwitchStatement(index)) {
                for(var groupProperty : serializerElement.groupProperties().entrySet()) {
                    var groupPropertyIndex = groupProperty.getValue().index();
                    switch (groupProperty.getValue().type()) {
                        case ProtobufPropertyType.MapType mapType -> writeMapDeserializer(
                                mapSwitchWriter,
                                groupPropertyIndex,
                                rawGroupData,
                                mapType
                        );
                        case ProtobufPropertyType.CollectionType collectionType -> writeDeserializer(
                                mapSwitchWriter,
                                getRawGroupCollectionFieldName(groupProperty),
                                groupPropertyIndex,
                                collectionType.value(),
                                true,
                                groupProperty.getValue().packed(),
                                rawGroupData
                        );
                        default -> writeDeserializer(
                                mapSwitchWriter,
                                rawGroupData,
                                groupPropertyIndex,
                                groupProperty.getValue().type(),
                                false,
                                groupProperty.getValue().packed(),
                                rawGroupData
                        );
                    }
                }
            }
        }
        methodWriter.println("%s.assertGroupClosed(%s);".formatted(INPUT_OBJECT_PARAMETER, INDEX_PARAMETER));
        methodWriter.printReturn(rawGroupData);
    }

    private String getRawGroupCollectionFieldName(Map.Entry<Integer, ProtobufGroupPropertyElement> property) {
        return "property" + property.getKey();
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

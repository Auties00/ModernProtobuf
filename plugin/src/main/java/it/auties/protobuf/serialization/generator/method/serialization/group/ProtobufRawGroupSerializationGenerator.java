package it.auties.protobuf.serialization.generator.method.serialization.group;

import it.auties.protobuf.serialization.generator.method.serialization.ProtobufSerializationGenerator;
import it.auties.protobuf.serialization.model.object.ProtobufObjectElement;
import it.auties.protobuf.serialization.model.property.ProtobufPropertyType;
import it.auties.protobuf.serialization.writer.ClassWriter;
import it.auties.protobuf.serialization.writer.MethodWriter;

import java.util.List;

// SPECIAL CASE: raw groups
// A raw group is a class-like type that represents a group, but isn't one, and has a serializer from itself to Map<Integer, Object>
// To serialize it, we need to declare a standalone method that takes:
//   - The index of the group declaration. This cannot be inlined because a raw group might be used as a type in different message properties with possibly different indexes
//   - The Map<Integer, Object> representation of the group
//   - The outputStream to serialize to
// This method cannot be inlined because a raw group might reference itself as a type in its properties
//
// Example:
//     private static void encodeType(int index, java.util.Map<Integer, Object> properties, ProtobufOutputStream protoOutputStream) {
//        protoOutputStream.writeGroupStart(index);
//        for (var recordProtoGroupEntry : properties.entrySet()) {
//            var recordProtoGroupEntryValue = recordProtoGroupEntry.getValue();
//            if (recordProtoGroupEntryValue != null) {
//                switch (recordProtoGroupEntry.getKey()) {
//                    ...
//                }
//            }
//        }
//        protoOutputStream.writeGroupEnd(index);
//    }
public class ProtobufRawGroupSerializationGenerator extends ProtobufSerializationGenerator {
    private static final String INDEX_PARAMETER = "protoGroupIndex";
    private static final String PROPERTIES_PARAMETER = "protoGroupProperties";
    private static final String OUTPUT_OBJECT_PARAMETER = "protoOutputStream";

    private static final String PROPERTY_NAME = "protoGroupEntry";
    private static final String PROPERTY_VALUE_NAME = "protoGroupEntryValue";

    public ProtobufRawGroupSerializationGenerator(ProtobufObjectElement objectElement) {
        super(objectElement);
    }

    @Override
    protected void doInstrumentation(ClassWriter classWriter, MethodWriter methodWriter) {
        // Opens the group at the provided index
        methodWriter.println("%s.writeGroupStart(%s);".formatted(OUTPUT_OBJECT_PARAMETER, INDEX_PARAMETER));

        // Loop through the input properties
        try (var forEachBody = methodWriter.printForEachStatement(PROPERTY_NAME, PROPERTIES_PARAMETER + ".entrySet()")) {
            // Declare a variable for the valueType of the property
            var propertyValueName = forEachBody.printVariableDeclaration(PROPERTY_VALUE_NAME, PROPERTY_NAME + ".getValue()");

            // Check if the property is not null, otherwise skip it
            try (var nullCheck = forEachBody.printIfStatement(propertyValueName + " != null")) {

                // Switch based on the property's index
                try (var switchBody = nullCheck.printSwitchStatement(PROPERTY_NAME + ".getKey()")) {

                    // Define all possible serialization paths based on the properties defined in @ProtobufSerializer
                    // PreliminaryChecks guarantees that groupProperties will be filled
                    for (var entry : objectElement.properties()) {

                        // Open the switch case for a given serialization path
                        try (var switchCaseBody = switchBody.printSwitchBranch(String.valueOf(entry.index()))) {

                            // Define serialization behaviour based on the type of the property
                            switch (entry.type()) {

                                // Repeated properties are supported by groups, so we handle them
                                case ProtobufPropertyType.CollectionType collectionType -> {
                                    // Cast the property's valueType to collection, as it's reasonable to assume that a repeated field is represented by a collection when serialized
                                    // The user could supply a non-collection type, but this is not a case that can be handled at compile time without complicated type checking analysis, paired with flow analysis
                                    var collectionField = "((java.util.Collection) %s)".formatted(propertyValueName);

                                    // Write the serialization logic
                                    writeRepeatedSerializer(
                                            switchCaseBody,
                                            entry.index(),
                                            PROPERTY_NAME,
                                            collectionField,
                                            collectionType,
                                            entry.packed(),
                                            false,
                                            true
                                    );
                                }

                                // Map properties are supported by groups, so we handle them
                                case ProtobufPropertyType.MapType mapType -> {
                                    // Cast the property's valueType to map, as it's reasonable to assume that a map field is represented by a map when serialized
                                    // The user could supply a non-map type, but this is not a case that can be handled at compile time without complicated type checking analysis, paired with flow analysis
                                    var mapField = "((java.util.Map<?, ?>) %s)".formatted(propertyValueName);

                                    // Write the serialization logic
                                    writeMapSerializer(
                                            switchCaseBody,
                                            entry.index(),
                                            PROPERTY_NAME + entry.index(),
                                            mapField,
                                            mapType,
                                            false,
                                            true
                                    );
                                }

                                // Write the serialization logic
                                default -> writeNormalSerializer(
                                        switchCaseBody,
                                        entry.index(),
                                        PROPERTY_NAME,
                                        propertyValueName,
                                        entry.type(),
                                        false,
                                        false,
                                        true
                                );
                            }
                        }
                    }
                }
            }
        }

        // Close the previously opened group
        methodWriter.println("%s.writeGroupEnd(%s);".formatted(OUTPUT_OBJECT_PARAMETER, INDEX_PARAMETER));
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
        return "void";
    }

    @Override
    protected List<String> parametersTypes() {
        return List.of("int", "Map<Integer, Object>", "ProtobufOutputStream");
    }

    @Override
    protected List<String> parametersNames() {
        return List.of(INDEX_PARAMETER, PROPERTIES_PARAMETER, OUTPUT_OBJECT_PARAMETER);
    }
}

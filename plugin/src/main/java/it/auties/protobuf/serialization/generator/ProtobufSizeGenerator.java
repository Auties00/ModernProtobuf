package it.auties.protobuf.serialization.generator;

import com.palantir.javapoet.*;
import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.model.ProtobufWireType;
import it.auties.protobuf.serialization.model.ProtobufObjectElement;
import it.auties.protobuf.serialization.model.ProtobufPropertyElement;
import it.auties.protobuf.serialization.model.ProtobufPropertyType;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.util.Map;

// Base class for size calculation method generators
// Computes the serialized byte size for protobuf messages including field tags, data, and nested structures
public abstract class ProtobufSizeGenerator extends ProtobufSerializationGenerator {
    public static final String METHOD_NAME = "sizeOf";
    private static final String INPUT_OBJECT_PARAMETER = "protoInputObject";
    private static final String OUTPUT_SIZE_NAME = "protoOutputSize";

    public ProtobufSizeGenerator(ProtobufObjectElement element) {
        super(element);
    }

    // Generates the helper method name for map entry size calculation
    // Example: "scores" -> "sizeOfScores"
    public static String getMapPropertyMethodName(String name) {
        return METHOD_NAME + name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    // Calculates the size of a repeated field
    //
    // For packed repeated fields (e.g., List<Integer> numbers with packed=true):
    //   Generated code: protoOutputSize += ProtobufOutputStream.getVarIntPackedSize(2, numbers);
    //
    // For non-packed repeated fields (e.g., List<String> names):
    //   Generated code:
    //     if (names != null) {
    //         for (var namesEntry : names) {
    //             protoOutputSize += ProtobufOutputStream.getFieldSize(1, WIRE_TYPE_LENGTH_DELIMITED);
    //             protoOutputSize += ProtobufOutputStream.getStringSize(namesEntry);
    //         }
    //     }
    protected void writeRepeatedSize(MethodSpec.Builder methodBuilder, long index, String name, String accessor, boolean packed, ProtobufPropertyType.CollectionType collectionType, boolean cast) {
        if(packed) {
            // Packed encoding: field_tag + varint(total_length) + all_elements_packed
            // Use optimized method that handles everything in one call
            var methodName = getPackedSizeCalculator(collectionType);
            methodBuilder.addStatement("$L += ProtobufOutputStream.$L($L, $L)", OUTPUT_SIZE_NAME, methodName, index, accessor);
            return;
        }

        // Non-packed encoding: iterate and calculate (field_tag + element) for each item
        methodBuilder.beginControlFlow("if ($L != null)", accessor);
        var repeatedEntryFieldName = name + "Entry";
        methodBuilder.beginControlFlow("for (var $L : $L)", repeatedEntryFieldName, accessor);
        writeNormalSize(
                methodBuilder,
                index,
                repeatedEntryFieldName,
                collectionType.valueType(),
                cast ? collectionType.valueType().serializedType() : null,
                null
        );
        methodBuilder.endControlFlow();
        methodBuilder.endControlFlow();
    }

    // Maps packed repeated field types to their size calculation method names
    // Fixed-size types (FIXED32/64) have known element sizes, varints have variable sizes
    private String getPackedSizeCalculator(ProtobufPropertyType.CollectionType collectionType) {
        return switch (collectionType.valueType().protobufType()) {
            case FLOAT, FIXED32, SFIXED32 -> "getFixed32PackedSize"; // 4 bytes per element
            case DOUBLE, FIXED64, SFIXED64 -> "getFixed64PackedSize"; // 8 bytes per element
            case BOOL -> "getBoolPackedSize"; // 1 byte per element
            case INT32, SINT32, UINT32, INT64, SINT64, UINT64 -> "getVarIntPackedSize"; // Variable size per element
            default ->
                    throw new IllegalArgumentException("Internal bug: unexpected packed type " + collectionType.valueType().protobufType());
        };
    }

    // Calculates the size of a map field by iterating entries and delegating to a helper method
    //
    // Example for Map<String, Integer> scores:
    //   Generated code in main method:
    //     var scoresMapField = protoInputObject.scores();
    //     if (scoresMapField != null) {
    //         for (var scoresMapEntry : scoresMapField.entrySet()) {
    //             protoOutputSize += ProtobufOutputStream.getFieldSize(3, WIRE_TYPE_LENGTH_DELIMITED);
    //             var scoresMapEntrySize = sizeOfScores(scoresMapEntry);
    //             protoOutputSize += ProtobufOutputStream.getVarIntSize(scoresMapEntrySize);
    //             protoOutputSize += scoresMapEntrySize;
    //         }
    //     }
    //
    //   Helper method (generated later via deferred operation):
    //     private static int sizeOfScores(Map.Entry<String, Integer> entry) { ... }
    protected void writeMapSize(TypeSpec.Builder classBuilder, MethodSpec.Builder methodBuilder, long index, String name, String accessor, ProtobufPropertyType.MapType mapType, boolean cast) {
        // Store map in local variable
        var mapFieldName = name + "MapField";
        methodBuilder.addStatement("var $L = $L", mapFieldName, accessor);

        // Queue generation of the helper method (will be created after main method completes)
        var methodName = getMapPropertyMethodName(name);
        deferredOperations.add(() -> writeMapEntryPropertySizeMethod(classBuilder, name, mapType, methodName, cast));

        // Iterate over map entries
        methodBuilder.beginControlFlow("if ($L != null)", mapFieldName);
        var mapEntryFieldName = name + "MapEntry";
        methodBuilder.beginControlFlow("for (var $L : $L.entrySet())", mapEntryFieldName, mapFieldName);

        // Field tag size for the map entry (always length-delimited)
        writeFieldTagSize(methodBuilder, index, ProtobufType.MAP);

        // Calculate size of this entry using helper method
        var mapEntrySizeFieldName = mapEntryFieldName + "Size";
        methodBuilder.addStatement("var $L = $L($L$L)", mapEntrySizeFieldName, methodName, cast ? "(java.util.Map.Entry) " : "", mapEntryFieldName);

        // Add varint size of entry length + actual entry size
        methodBuilder.addStatement("$L += ProtobufOutputStream.getVarIntSize($L)", OUTPUT_SIZE_NAME, mapEntrySizeFieldName);
        methodBuilder.addStatement("$L += $L", OUTPUT_SIZE_NAME, mapEntrySizeFieldName);
        methodBuilder.endControlFlow();
        methodBuilder.endControlFlow();
    }

    // Generates a private helper method that calculates the size of a single map entry
    //
    // Example for Map<String, Integer> scores:
    //   Generated method:
    //     private static int sizeOfScores(Map.Entry<String, Integer> protoInputObject) {
    //         var protoOutputSize = 0;
    //         // Key (field 1 in map entry message)
    //         protoOutputSize += ProtobufOutputStream.getFieldSize(1, WIRE_TYPE_LENGTH_DELIMITED);
    //         var scoresMapKey = protoInputObject.getKey();
    //         if (scoresMapKey != null) {
    //             protoOutputSize += ProtobufOutputStream.getStringSize(scoresMapKey);
    //         }
    //         // Value (field 2 in map entry message)
    //         protoOutputSize += ProtobufOutputStream.getFieldSize(2, WIRE_TYPE_VAR_INT);
    //         var scoresMapValue = protoInputObject.getValue();
    //         protoOutputSize += ProtobufOutputStream.getVarIntSize(scoresMapValue);
    //         return protoOutputSize;
    //     }
    private void writeMapEntryPropertySizeMethod(TypeSpec.Builder classBuilder, String name, ProtobufPropertyType.MapType mapType, String methodName, boolean cast) {
        // Build parameter type: Map.Entry<KeyType, ValueType>
        var keyTypeName = ClassName.bestGuess(getQualifiedName(mapType.keyType().accessorType()));
        var valueTypeName = ClassName.bestGuess(getQualifiedName(mapType.valueType().accessorType()));

        // Create the helper method signature
        var mapEntryMethodBuilder = MethodSpec.methodBuilder(methodName);
        mapEntryMethodBuilder.addModifiers(Modifier.PRIVATE, Modifier.STATIC);
        mapEntryMethodBuilder.returns(TypeName.INT);
        mapEntryMethodBuilder.addParameter(ParameterSpec.builder(
                ParameterizedTypeName.get(ClassName.get(Map.Entry.class), keyTypeName, valueTypeName),
                INPUT_OBJECT_PARAMETER
        ).build());

        // Initialize size accumulator
        mapEntryMethodBuilder.addStatement("var $L = 0", OUTPUT_SIZE_NAME);

        // Calculate key size (field index 1 in map entry message)
        writeNormalSize(
                mapEntryMethodBuilder,
                1,
                name + "MapKey",
                mapType.keyType(),
                cast ? mapType.keyType().serializedType() : null,
                INPUT_OBJECT_PARAMETER + ".getKey()"
        );

        // Calculate value size (field index 2 in map entry message)
        writeNormalSize(
                mapEntryMethodBuilder,
                2,
                name + "MapValue",
                mapType.valueType(),
                cast ? mapType.valueType().serializedType() : null,
                INPUT_OBJECT_PARAMETER + ".getValue()"
        );

        // Return total size
        mapEntryMethodBuilder.addStatement("return $L", OUTPUT_SIZE_NAME);
        classBuilder.addMethod(mapEntryMethodBuilder.build());
    }

    // Convenience overload that extracts accessor call from property and delegates to main writeNormalSize
    protected void writeNormalSize(MethodSpec.Builder methodBuilder, ProtobufPropertyElement property) {
        var accessorCall = getAccessorCall(INPUT_OBJECT_PARAMETER, property.accessor());
        writeNormalSize(
                methodBuilder,
                property.index(),
                property.name(),
                property.type(),
                null,
                accessorCall
        );
    }

    // Calculates size for a normal (non-repeated, non-map) field
    // Delegates to writeCustomSerializer which handles custom serializers, null checks, and type casting
    // Then routes to either writeObjectCalculator (MESSAGE/ENUM/GROUP) or writePrimitiveCalculator (primitives/strings)
    //
    // Example for String name:
    //   Generated code:
    //     protoOutputSize += ProtobufOutputStream.getFieldSize(1, WIRE_TYPE_LENGTH_DELIMITED);
    //     var name = protoInputObject.name();
    //     if (name != null) {
    //         protoOutputSize += ProtobufOutputStream.getStringSize(name);
    //     }
    protected void writeNormalSize(MethodSpec.Builder methodBuilder, long index, String name, ProtobufPropertyType normalType, TypeMirror castType, String accessorCall) {
        writeCustomSerializer(
                methodBuilder,
                index,
                name,
                accessorCall == null ? name : accessorCall,
                normalType,
                accessorCall != null, // declareVariable
                true, // variableNullCheck
                castType != null, // cast
                // Handler for object types (MESSAGE/ENUM/GROUP)
                (nestedBuilder, serializedName, _) -> writeObjectCalculator(
                        nestedBuilder,
                        index,
                        name,
                        normalType,
                        castType,
                        serializedName
                ),
                // Handler for primitive types
                (nestedBuilder, serializedName, _) -> writePrimitiveCalculator(
                        nestedBuilder,
                        index,
                        normalType.protobufType(),
                        castType,
                        serializedName
                )
        );
    }

    // Calculates size for MESSAGE, ENUM, and GROUP types
    // These types require recursive calls to their respective Spec classes
    //
    // For MESSAGE: size = field_tag + varint(message_length) + message_bytes
    // For ENUM: size = field_tag + enum_value_bytes (no length prefix)
    // For GROUP: size = field_tag_start + group_contents + field_tag_end (computed by group's sizeOf)
    private void writeObjectCalculator(MethodSpec.Builder methodBuilder, long index, String name, ProtobufPropertyType type, TypeMirror castType, String accessor) {
        // Add field tag size
        writeFieldTagSize(methodBuilder, index, type.protobufType());

        switch (type.protobufType()) {
            case MESSAGE, ENUM -> {
                // Get the type after applying custom serializers (if any)
                var parameterType = type.serializers().isEmpty() ? type.descriptorElementType() : type.serializers().getLast().parameterType();
                var specName = getSpecFromObject(parameterType);

                // Recursively call the nested type's sizeOf method
                var serializedObjectFieldName = name + "SerializedSize";
                methodBuilder.addStatement("var $L = $L.$L($L)", serializedObjectFieldName, specName, name(), accessor);

                // For messages: add the varint size of the length prefix
                if (!isEnum(parameterType)) {
                    methodBuilder.addStatement("$L += ProtobufOutputStream.getVarIntSize($L)", OUTPUT_SIZE_NAME, serializedObjectFieldName);
                }

                // Add the actual message/enum size
                methodBuilder.addStatement("$L += $L", OUTPUT_SIZE_NAME, serializedObjectFieldName);
            }
            case GROUP -> {
                // Groups can have raw serializers (for backward compatibility)
                var lastSerializer = type.rawGroupSerializer()
                        .orElse(null);
                if (lastSerializer != null) {
                    // Raw group: pass group index to sizeOf
                    var rawGroupSpecType = getSpecFromObject(lastSerializer.parameterType());
                    methodBuilder.addStatement("$L += $L.$L($L, $L$L)", OUTPUT_SIZE_NAME, rawGroupSpecType, name(), index, castType != null ? "(java.util.Map) " : "", accessor);
                } else {
                    // Standard group: get size from group's Spec class
                    var groupType = type.serializers().isEmpty() ? type.descriptorElementType() : type.serializers().getLast().parameterType();
                    var groupSpecType = getSpecFromObject(groupType);
                    var serializedObjectFieldName = name + "SerializedSize";
                    methodBuilder.addStatement("var $L = $L.$L($L, $L$L)", serializedObjectFieldName, groupSpecType, name(), index, castType != null ? "(" + castType + ") " : "", accessor);
                    methodBuilder.addStatement("$L += $L", OUTPUT_SIZE_NAME, serializedObjectFieldName);
                }
            }
            default -> throw new IllegalArgumentException("Internal bug: %s property types should not reach writeObjectCalculator".formatted(type.protobufType().name()));
        }
    }

    // Calculates size for primitive types (strings, numbers, bytes, bools)
    // Size calculation depends on the wire type:
    //   - Fixed-size types (FIXED32/64, FLOAT/DOUBLE, BOOL): constant size
    //   - Variable-size types (STRING, BYTES, varints): calculated dynamically
    //
    // Example sizes:
    //   BOOL: 1 byte
    //   FIXED32/SFIXED32/FLOAT: 4 bytes
    //   FIXED64/SFIXED64/DOUBLE: 8 bytes
    //   STRING: varint(length) + actual_bytes
    //   INT32/INT64/etc: varint encoding (1-10 bytes depending on value)
    private void writePrimitiveCalculator(MethodSpec.Builder methodBuilder, long index, ProtobufType protobufType, TypeMirror castType, String accessor) {
        // Add field tag size
        writeFieldTagSize(methodBuilder, index, protobufType);

        // Calculate data size based on type
        var protobufSize = switch (protobufType) {
            case BOOL -> "1"; // Always 1 byte
            case STRING -> "ProtobufOutputStream.getStringSize(%s%s)".formatted(castType != null ? "(" + castType + ") " : "", accessor);
            case BYTES -> "ProtobufOutputStream.getBytesSize(%s%s)".formatted(castType != null ? "(" + castType + ") " : "", accessor);
            case ENUM, INT32, SINT32, UINT32, INT64, SINT64, UINT64 ->
                    "ProtobufOutputStream.getVarIntSize(%s%s)".formatted(castType != null ? "(" + castType + ") " : "", accessor);
            case FIXED32, SFIXED32, FLOAT -> "4"; // Always 4 bytes
            case FIXED64, SFIXED64, DOUBLE -> "8"; // Always 8 bytes
            default -> throw new IllegalArgumentException("Internal bug: %s property types should not reach writePrimitiveCalculator".formatted(protobufType.name()));
        };
        methodBuilder.addStatement("$L += $L", OUTPUT_SIZE_NAME, protobufSize);
    }

    // Calculates the size of a field tag (encoded as varint: field_number << 3 | wire_type)
    // Field tags are present for all field types except GROUP (which has start/end tags handled separately)
    //
    // Wire type mapping:
    //   0 (VARINT): int32, int64, uint32, uint64, sint32, sint64, bool, enum
    //   1 (FIXED64): fixed64, sfixed64, double
    //   2 (LENGTH_DELIMITED): string, bytes, embedded messages, packed repeated fields, maps
    //   5 (FIXED32): fixed32, sfixed32, float
    private void writeFieldTagSize(MethodSpec.Builder methodBuilder, long index, ProtobufType protobufType) {
        // Groups don't use field tags (they use start/end group markers)
        if(protobufType == ProtobufType.GROUP) {
            return;
        }

        // Map protobuf type to wire type
        var wireType = switch (protobufType) {
            case GROUP -> throw new IllegalArgumentException("Internal bug: group property types should not reach writeFieldTagSize");
            case MESSAGE, ENUM, STRING, BYTES, MAP -> ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED; // Wire type 2
            case FLOAT, FIXED32, SFIXED32 -> ProtobufWireType.WIRE_TYPE_FIXED32; // Wire type 5
            case DOUBLE, SFIXED64, FIXED64 -> ProtobufWireType.WIRE_TYPE_FIXED64; // Wire type 1
            case BOOL, INT32, SINT32, UINT32, INT64, UINT64, SINT64 -> ProtobufWireType.WIRE_TYPE_VAR_INT; // Wire type 0
            case UNKNOWN -> throw new IllegalArgumentException("Internal bug: unknown property types should not reach writeFieldTagSize");
        };

        // Add the varint size of (field_number << 3 | wire_type)
        methodBuilder.addStatement("$L += ProtobufOutputStream.getFieldSize($L, $L)", OUTPUT_SIZE_NAME, index, wireType);
    }

    @Override
    public boolean shouldInstrument() {
        return true;
    }

    @Override
    protected TypeName returnType() {
        return TypeName.INT;
    }

    @Override
    public String name() {
        return METHOD_NAME;
    }
}

package it.auties.protobuf.serialization.generator;

import com.palantir.javapoet.CodeBlock;
import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.serialization.model.ProtobufObjectElement;
import it.auties.protobuf.serialization.model.ProtobufPropertyType;

import javax.lang.model.element.Modifier;
import java.util.List;

// Base class for deserialization method generators with shared logic for decoding protobuf values
// Provides reusable deserialization logic for normal, repeated, and map fields with custom @ProtobufDeserializer support
public abstract class ProtobufDeserializationGenerator extends ProtobufMethodGenerator {
    public static final String METHOD_NAME = "decode";
    private static final String INPUT_STREAM_NAME = "protoInputStream";

    public ProtobufDeserializationGenerator(ProtobufObjectElement element) {
        super(element);
    }

    // Generates a switch case block that deserializes a map field from the protobuf stream
    //
    // Example generated block for input name = "scores":
    //   var scoresInputStream = protoInputStream.readLengthDelimited();
    //   String scoresKey = null;
    //   Integer scoresValue = null;
    //   while (scoresInputStream.readTag()) {
    //      switch (scoresInputStream.index()) {
    //         case 1L:
    //            scoresKey = scoresInputStream.readString();
    //            break;
    //         case 2L:
    //            scoresValue = scoresInputStream.readInt32();
    //            break;
    //      }
    //   }
    //   scores.put(scoresKey, scoresValue);
    //   break;
    protected CodeBlock writeMapDeserializer(String name, ProtobufPropertyType.MapType mapType) {
        var caseBlock = CodeBlock.builder();

        // Read the length-delimited map entry into a new stream
        var streamName = "%sInputStream".formatted(name);
        caseBlock.addStatement("var $L = $L.readLengthDelimited()", streamName, INPUT_STREAM_NAME);

        // Declare key and value variables
        var keyTypeName = getQualifiedName(mapType.keyType().accessorType());
        var valueTypeName = getQualifiedName(mapType.valueType().accessorType());
        var keyName = "%sKey".formatted(name);
        var valueName = "%sValue".formatted(name);
        caseBlock.addStatement("$L $L = null", keyTypeName, keyName);
        caseBlock.addStatement("$L $L = null", valueTypeName, valueName);

        // Get the read methods and conversion chains for key and value
        var keyReadMethod = getDeserializerStreamMethod(mapType.keyType(), false);
        var keyReadFunction = getConvertedValue(streamName, mapType.keyType(), keyReadMethod);
        var valueReadMethod = getDeserializerStreamMethod(mapType.valueType(), false);
        var valueReadFunction = getConvertedValue(streamName, mapType.valueType(), valueReadMethod);

        // Read the map entry stream (field 1 = key, field 2 = value)
        caseBlock.beginControlFlow("while ($L.readTag())", streamName);
        caseBlock.beginControlFlow("switch ($L.index())", streamName);
        caseBlock.add("case 1L:\n").indent();
        caseBlock.addStatement("$L = $L", keyName, keyReadFunction);
        caseBlock.addStatement("break");
        caseBlock.unindent();
        caseBlock.add("case 2L:\n").indent();
        caseBlock.addStatement("$L = $L", valueName, valueReadFunction);
        caseBlock.addStatement("break");
        caseBlock.unindent();
        caseBlock.endControlFlow();
        caseBlock.endControlFlow();

        // Put the deserialized entry into the map
        caseBlock.addStatement("$L.put($L, $L)", name, keyName, valueName);
        caseBlock.addStatement("break");
        return caseBlock.build();
    }

    // Generates a switch case block that deserializes a normal or repeated field
    //
    // Example for normal field: String name;
    // Generated code:
    //       name = protoInputStream.readString();
    //       break;
    //
    // Example for repeated field: List<Integer> numbers;
    // Generated code:
    //       numbers.add(protoInputStream.readInt32());
    //       break;
    protected CodeBlock writeDeserializer(String name, ProtobufPropertyType type, boolean repeated, boolean packed) {
        // Get the stream read method (e.g., "readString", "readInt32", "readInt32Packed")
        var readMethod = getDeserializerStreamMethod(type, packed);

        // Build the complete read expression with custom deserializers applied
        var readFunction = getConvertedValue(INPUT_STREAM_NAME, type, readMethod);

        // Generate the assignment (direct assignment vs add/addAll for collections)
        var readAssignment = getReadAssignment(name, repeated, packed, readFunction);

        // Create the case block
        return CodeBlock.builder()
                .addStatement("$L", readAssignment)
                .addStatement("break")
                .build();
    }

    // Generates the assignment statement for reading a value
    // Returns "name = value" for non-repeated, "name.add(value)" for repeated, "name.addAll(value)" for packed
    private String getReadAssignment(String name, boolean repeated, boolean packed, String readFunction) {
        if (repeated) {
            // For repeated fields: add() for individual elements, addAll() for packed (returns collection)
            var repeatedMethod = packed ? "addAll" : "add";
            return "%s.%s(%s)".formatted(name, repeatedMethod, readFunction);
        } else {
            // Direct assignment for single values
            return "%s = %s".formatted(name, readFunction);
        }
    }

    // Chains together the stream read method and custom deserializers to build the complete read expression
    //
    // Example with custom deserializer:
    //   Input: @ProtobufDeserializer(method = "fromProto") LocalDateTime timestamp;
    //   Returns: "MyConverter.fromProto(protoInputStream.readInt64())"
    //
    // Flow:
    //   1. Start with "protoInputStream"
    //   2. For MESSAGE: wrap in readLengthDelimited() -> "protoInputStream.readLengthDelimited()"
    //   3. Add stream method: "protoInputStream.readLengthDelimited().readInt64()"
    //   4. Chain deserializers: "MyConverter.fromProto(protoInputStream.readInt64())"
    private String getConvertedValue(String value, ProtobufPropertyType implementation, String readMethod) {
        // For MESSAGE types, read the length-delimited nested message first
        if (implementation.protobufType() == ProtobufType.MESSAGE) {
            value = "%s.readLengthDelimited()".formatted(value);
        }

        // Append the stream read method if it exists (empty for MESSAGE/GROUP)
        if(!readMethod.isEmpty()) {
            value = "%s.%s()".formatted(value, readMethod);
        }

        // Apply each custom deserializer in the chain
        for (var i = 0; i < implementation.deserializers().size(); i++) {
            var deserializer = implementation.deserializers().get(i);
            value = "%s.%s(%s)".formatted(deserializer.delegate().ownerName(), deserializer.delegate().name(), value);
        }

        return value;
    }

    // Maps protobuf types to their corresponding ProtobufInputStream read method names
    // Returns empty string for MESSAGE/GROUP (handled separately with readLengthDelimited)
    // Packed variants return collections (e.g., readInt32Packed returns List<Integer>)
    private String getDeserializerStreamMethod(ProtobufPropertyType type, boolean packed) {
        return switch (type.protobufType()) {
            case STRING -> "readString";
            case UNKNOWN -> throw new IllegalArgumentException("Internal bug: unknown types should not reach getDeserializerStreamMethod");
            case MESSAGE, GROUP -> ""; // Handled separately
            case ENUM, INT32, SINT32, UINT32 -> packed ? "readInt32Packed" : "readInt32";
            case BYTES -> "readBytes";
            case BOOL -> packed ? "readBoolPacked" : "readBool";
            case MAP -> throw new IllegalArgumentException("Internal bug: map types should not reach getDeserializerStreamMethod");
            case FLOAT -> packed ? "readFloatPacked" : "readFloat";
            case DOUBLE -> packed ? "readDoublePacked" : "readDouble";
            case FIXED32, SFIXED32 -> packed ? "readFixed32Packed" : "readFixed32";
            case INT64, SINT64, UINT64 -> packed ? "readInt64Packed" : "readInt64";
            case FIXED64, SFIXED64 -> packed ? "readFixed64Packed" : "readFixed64";
        };
    }

    @Override
    protected List<Modifier> modifiers() {
        return List.of(Modifier.PUBLIC, Modifier.STATIC);
    }

    @Override
    protected String name() {
        return METHOD_NAME;
    }
}

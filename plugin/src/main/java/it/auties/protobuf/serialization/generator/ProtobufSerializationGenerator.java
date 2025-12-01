package it.auties.protobuf.serialization.generator;

import com.palantir.javapoet.MethodSpec;
import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.serialization.model.ProtobufConverterElement;
import it.auties.protobuf.serialization.model.ProtobufPropertyType;
import it.auties.protobuf.serialization.model.ProtobufObjectElement;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Base class for serialization method generators with shared logic for encoding protobuf values
// Handles serialization of normal, repeated, and map fields with support for custom @ProtobufSerializer chains
public abstract class ProtobufSerializationGenerator extends ProtobufMethodGenerator {
    public static final String METHOD_NAME = "encode";
    private static final String OUTPUT_OBJECT_PARAMETER = "protoOutputStream";

    public ProtobufSerializationGenerator(ProtobufObjectElement element) {
        super(element);
    }

    // Serializes a repeated field to the protobuf stream
    //
    // For packed repeated fields (e.g., List<Integer> numbers with packed=true):
    //   Generated code: protoOutputStream.writeInt32Packed(2, numbers);
    //
    // For non-packed repeated fields (e.g., List<String> names):
    //   Generated code:
    //     if (names != null) {
    //         for (var namesEntry : names) {
    //             protoOutputStream.writeString(1, namesEntry);
    //         }
    //     }
    protected void writeRepeatedSerializer(MethodSpec.Builder methodBuilder, long index, String name, String accessor, ProtobufPropertyType.CollectionType collectionType, boolean packed, boolean nullCheck, boolean cast) {
        if(packed) {
            // Packed encoding: write all elements at once using packed method
            var writeMethod = getStreamMethodName(collectionType.valueType().protobufType(), true);
            methodBuilder.addStatement("$L.$L($L, $L)", OUTPUT_OBJECT_PARAMETER, writeMethod.orElseThrow(), index, accessor);
        }else {
            // Non-packed encoding: iterate and write each element individually
            if(nullCheck) {
                methodBuilder.beginControlFlow("if ($L != null)", accessor);
            }
            var localVariableName = "%sEntry".formatted(name);
            methodBuilder.beginControlFlow("for (var $L : $L)", localVariableName, accessor);
            writeNormalSerializer(methodBuilder, index, name, localVariableName, collectionType.valueType(), false, true, cast);
            methodBuilder.endControlFlow();
            if(nullCheck) {
                methodBuilder.endControlFlow();
            }
        }
    }

    // Serializes a map field by writing each entry as a length-delimited message
    //
    // Example for Map<String, Integer> scores:
    //   Generated code:
    //     if (scores != null) {
    //         for (var scoresEntry : scores.entrySet()) {
    //             protoOutputStream.writeMessage(3, sizeOfScores(scoresEntry));
    //             // Write key (field 1)
    //             protoOutputStream.writeString(1, scoresEntry.getKey());
    //             // Write value (field 2)
    //             var scoresValue = scoresEntry.getValue();
    //             if (scoresValue != null) {
    //                 protoOutputStream.writeInt32(2, scoresValue);
    //             }
    //         }
    //     }
    protected void writeMapSerializer(MethodSpec.Builder methodBuilder, long index, String name, String accessor, ProtobufPropertyType.MapType mapType) {
        // Null check the map
        methodBuilder.beginControlFlow("if ($L != null)", accessor);
        var localVariableName = "%sEntry".formatted(name);

        // Iterate over map entries
        methodBuilder.beginControlFlow("for (var $L : $L.entrySet())", localVariableName, accessor);

        // Write the map entry as a message (calls size calculator to get length)
        var methodName = ProtobufSizeGenerator.getMapPropertyMethodName(name);
        methodBuilder.addStatement("$L.writeMessage($L, $L($L))", OUTPUT_OBJECT_PARAMETER, index, methodName, localVariableName);

        // Write key (field index 1 in map entry message)
        writeNormalSerializer(
                methodBuilder,
                1,
                name + "Key",
                "%s.getKey()".formatted(localVariableName),
                mapType.keyType(),
                false,
                false,
                false
        );

        // Write value (field index 2 in map entry message)
        writeNormalSerializer(
                methodBuilder,
                2,
                name + "Value",
                "%s.getValue()".formatted(localVariableName),
                mapType.valueType(),
                true,
                true,
                false
        );

        methodBuilder.endControlFlow();
        methodBuilder.endControlFlow();
    }

    // Convenience wrapper that delegates to writeCustomSerializer with default handlers
    // Simply adds the serialization statements to the method builder
    protected void writeNormalSerializer(MethodSpec.Builder methodBuilder, long index, String name, String value, ProtobufPropertyType type, boolean declareVariable, boolean variableNullCheck, boolean cast) {
        writeCustomSerializer(
                methodBuilder,
                index,
                name,
                value,
                type,
                declareVariable,
                variableNullCheck,
                cast,
                // Both object and primitive handlers just add statements directly
                (builder, serializedName, serializerStatements) -> {
                    for(var serializerStatement : serializerStatements) {
                        builder.addStatement("$L", serializerStatement);
                    }
                },
                (builder, serializedName, serializerStatements) -> {
                    for(var serializerStatement : serializerStatements) {
                        builder.addStatement("$L", serializerStatement);
                    }
                }
        );
    }

    // Writes code to serialize a field with optional custom serializer chain
    //
    // Example with custom serializer:
    //   Input: @ProtobufSerializer(method = "toProto") LocalDateTime timestamp;
    //   Generated code:
    //     var timestamp = protoInputObject.timestamp();
    //     if (timestamp != null) {
    //         var timestamp0 = MyConverter.toProto(timestamp);
    //         if (timestamp0 != null) {
    //             protoOutputStream.writeInt64(1, timestamp0);
    //         }
    //     }
    //
    // Flow:
    //   1. Apply cast (if needed for generics)
    //   2. Declare variable (if needed to avoid repeated accessor calls)
    //   3. Null check initial value (if non-primitive)
    //   4. Apply each serializer in chain, null checking intermediate results
    //   5. Write final value to stream using appropriate writeXxx() method
    //   6. Close all null-check blocks
    protected void writeCustomSerializer(MethodSpec.Builder methodBuilder, long index, String name, String value, ProtobufPropertyType type, boolean declareVariable, boolean variableNullCheck, boolean cast, CustomSerializerHandler objectWriter, CustomSerializerHandler streamWriter) {
        // Step 1: Apply cast if necessary (for generics/wildcards)
        if(cast) {
            var castType = getQualifiedName(type.descriptorElementType());
            value = "((%s) %s)".formatted(castType, value);
        }

        // Step 2: Declare a variable to avoid repeated accessor calls
        var propertyName = value;
        if(declareVariable) {
            methodBuilder.addStatement("var $L = $L", name, value);
            propertyName = name;
        }

        // Get the stream write method for the final result (empty for MESSAGE/ENUM/GROUP)
        var writeMethod = getStreamMethodName(type.protobufType(), false);

        // Track how many null-check blocks we open (so we can close them all at the end)
        var controlFlowDepth = 0;

        // Step 3: Null check the initial value if it's a non-primitive type
        if(variableNullCheck && !(type.accessorType() instanceof PrimitiveType)) {
            methodBuilder.beginControlFlow("if ($L != null)", propertyName);
            controlFlowDepth++;
        }

        // Step 4: Apply each custom serializer in the chain
        var serializers = type.serializers();
        var object = isObject(type);
        for(var i = 0; i < serializers.size(); i++) {
            var serializer = serializers.get(i);

            // Build the serializer invocation (e.g., "MyConverter.toProto(value)")
            var result = createSerializerInvocation(serializer, propertyName, index);

            // Check if this is the last serializer and it's an object type or void
            var lastSerializer = i == serializers.size() - 1;
            if ((lastSerializer && writeMethod.isEmpty()) || serializer.returnType().getKind() == TypeKind.VOID) {
                // For MESSAGE types, we need to write the message header first
                var statements = new ArrayList<String>();
                if(type.protobufType() == ProtobufType.MESSAGE) {
                    statements.add(getMessageMethod(index, serializer, propertyName));
                }
                statements.add("%s".formatted(result));
                objectWriter.handle(methodBuilder, propertyName, statements);
                continue;
            }

            // Store the result of this serialization round in a new variable
            var newPropertyName = name + i;
            methodBuilder.addStatement("var $L = $L", newPropertyName, result);

            // Null check the result if it's not a primitive and not the last object serializer
            if ((object && lastSerializer) || serializer.returnType() instanceof PrimitiveType) {
                propertyName = newPropertyName;
                continue;
            }

            methodBuilder.beginControlFlow("if ($L != null)", newPropertyName);
            controlFlowDepth++;
            propertyName = newPropertyName;
        }

        // Step 5: Write the final value to the stream
        if(writeMethod.isPresent()) {
            var result = "%s.%s(%s, %s%s)".formatted(
                    OUTPUT_OBJECT_PARAMETER,
                    writeMethod.get(),
                    index,
                    cast ? "(%s) ".formatted(type.protobufType().deserializableType().getName()) : "",
                    propertyName
            );
            streamWriter.handle(methodBuilder, propertyName, List.of(result));
        }

        // Step 6: Close all null-check control flows
        for (int i = 0; i < controlFlowDepth; i++) {
            methodBuilder.endControlFlow();
        }
    }

    // Generates code to write a message header (calls sizeOf to get length)
    // Returns: "protoOutputStream.writeMessage(index, MessageSpec.sizeOf(value))"
    private String getMessageMethod(long index, ProtobufConverterElement.Attributed.Serializer serializer, String propertyName) {
        return "%s.writeMessage(%s, %s.%s(%s))".formatted(
                OUTPUT_OBJECT_PARAMETER,
                index,
                serializer.delegate().ownerName(),
                ProtobufSizeGenerator.METHOD_NAME,
                propertyName
        );
    }

    // Checks if a type is a complex object (MESSAGE/ENUM/GROUP) vs a primitive
    private boolean isObject(ProtobufPropertyType type) {
        return type.protobufType() == ProtobufType.MESSAGE
                || type.protobufType() == ProtobufType.ENUM
                || type.protobufType() == ProtobufType.GROUP;
    }

    // Callback interface for handling the final write operation
    // Allows different strategies for writing object vs primitive types
    protected interface CustomSerializerHandler {
        void handle(MethodSpec.Builder builder, String value, List<String> statements);
    }

    // Builds a serializer method invocation string based on the serializer's signature
    //
    // For instance methods (non-static):
    //   Returns: "value.toProto()"
    //
    // For static methods with 1 parameter:
    //   Returns: "MyConverter.toProto(value)"
    //
    // For Spec class encode methods (2 parameters - MESSAGE/ENUM):
    //   Returns: "MessageSpec.encode(value, protoOutputStream)"
    //
    // For Spec class encode methods (3 parameters - GROUP):
    //   Returns: "GroupSpec.encode(groupIndex, value, protoOutputStream)"
    private String createSerializerInvocation(ProtobufConverterElement.Attributed.Serializer serializer, String value, int groupIndex) {
        // Instance method: call on the value object
        if (!serializer.delegate().modifiers().contains(Modifier.STATIC)) {
            return "%s.%s()".formatted(value, serializer.delegate().name());
        }

        // Static method: dispatch based on parameter count
        return switch (serializer.delegate().parameters().size()) {
            // Normal mixin serializer: static method taking just the value
            case 1 -> "%s.%s(%s)".formatted(
                   serializer.delegate().ownerName(),
                    serializer.delegate().name(),
                    value
            );

            // Synthetic Spec serializer for MESSAGE/ENUM: takes value + stream
            case 2 -> "%s.%s(%s, %s)".formatted(
                    serializer.delegate().ownerName(),
                    serializer.delegate().name(),
                    value,
                    OUTPUT_OBJECT_PARAMETER
            );

            // Synthetic Spec serializer for GROUP: takes group index + value + stream
            case 3 -> "%s.%s(%s, %s, %s)".formatted(
                    serializer.delegate().ownerName(),
                    serializer.delegate().name(),
                    groupIndex,
                    value,
                    OUTPUT_OBJECT_PARAMETER
            );

            default -> throw new IllegalArgumentException(
                    "Unexpected number of arguments for serializer "
                            +  serializer.delegate().name()
                            + " in "
                            + serializer.delegate().ownerName()
            );
        };
    }

    // Maps protobuf types to their corresponding ProtobufOutputStream write method names
    // Returns empty Optional for MESSAGE/GROUP (handled separately with synthetic serializers)
    // Packed variants write entire collections at once (e.g., writeInt32Packed)
    private Optional<String> getStreamMethodName(ProtobufType protobufType, boolean packed) {
        var result = switch (protobufType) {
            case STRING -> "writeString";
            case UNKNOWN -> throw new IllegalArgumentException("Internal bug: unknown types should not reach getSerializerStreamMethod");
            case ENUM, INT32, SINT32 -> "writeInt32";
            case MESSAGE, GROUP -> null; // Handled separately
            case BYTES -> "writeBytes";
            case BOOL -> "writeBool";
            case UINT32 -> "writeUInt32";
            case MAP -> throw new IllegalArgumentException("Internal bug: map types should not reach getSerializerStreamMethod");
            case FLOAT -> "writeFloat";
            case DOUBLE -> "writeDouble";
            case FIXED32, SFIXED32 -> "writeFixed32";
            case INT64, SINT64 -> "writeInt64";
            case UINT64 -> "writeUInt64";
            case FIXED64, SFIXED64 -> "writeFixed64";
        };

        // Append "Packed" suffix for packed repeated fields
        if(result != null && packed) {
            return Optional.of(result + "Packed");
        }

        return Optional.ofNullable(result);
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

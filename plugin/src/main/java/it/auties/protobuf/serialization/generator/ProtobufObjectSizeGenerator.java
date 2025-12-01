package it.auties.protobuf.serialization.generator;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import it.auties.protobuf.serialization.model.ProtobufObjectElement;
import it.auties.protobuf.serialization.model.ProtobufObjectElement.Type;
import it.auties.protobuf.serialization.model.ProtobufPropertyType;

import javax.lang.model.element.Element;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

// Generates a method that calculates the serialized size of a protobuf object
//
// Example Input:
//   @ProtobufMessage
//   public record Person(
//       @ProtobufProperty(index = 1) String name,
//       @ProtobufProperty(index = 2) int age
//   ) {}
//
// Example Output:
//   public static int sizeOf(Person protoInputObject) {
//       if (protoInputObject == null) {
//           return 0;
//       }
//       var protoOutputSize = 0;
//       // Field tag size for field 1 (field index + wire type)
//       protoOutputSize += ProtobufOutputStream.getFieldSize(1, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
//       var name = protoInputObject.name();
//       if (name != null) {
//           // String size = length of string + varint size of length
//           protoOutputSize += ProtobufOutputStream.getStringSize(name);
//       }
//       // Field tag size for field 2
//       protoOutputSize += ProtobufOutputStream.getFieldSize(2, ProtobufWireType.WIRE_TYPE_VAR_INT);
//       var age = protoInputObject.age();
//       // VarInt size depends on value magnitude
//       protoOutputSize += ProtobufOutputStream.getVarIntSize(age);
//       return protoOutputSize;
//   }
//
// For Enums:
//   Input: @ProtobufEnum enum Status { ACTIVE, INACTIVE }
//   Output:
//     public static int sizeOf(Status protoInputObject) {
//         if (protoInputObject == null) { return 0; }
//         return ProtobufOutputStream.getVarIntSize(protoInputObject.ordinal());
//     }
//
// Execution Flow:
//   1. Return 0 if input is null
//   2. For enums: Calculate varint size of ordinal/field value
//   3. For messages/groups:
//      a. Initialize size accumulator to 0
//      b. Add group start/end marker sizes (if group type)
//      c. For each property, calculate and accumulate:
//         - Field tag size (field index + wire type encoded as varint)
//         - Data size based on type:
//           * Fixed-size types (int32/64, fixed32/64, etc): constant size
//           * Variable-size types (varint, string, bytes): calculated dynamically
//           * Nested messages: recursively call their sizeOf() method
//           * Repeated fields: sum sizes of all elements
//           * Map fields: create helper method to calculate entry size
//      d. Return total accumulated size
public class ProtobufObjectSizeGenerator extends ProtobufSizeGenerator {
    private static final String INPUT_OBJECT_PARAMETER = "protoInputObject";
    private static final String OUTPUT_SIZE_NAME = "protoOutputSize";
    private static final String GROUP_INDEX_PARAMETER = "protoGroupIndex";

    public ProtobufObjectSizeGenerator(ProtobufObjectElement element) {
        super(element);
    }

    @Override
    protected void doInstrumentation(TypeSpec.Builder classBuilder, MethodSpec.Builder methodBuilder) {
        methodBuilder.beginControlFlow("if ($L == null)", INPUT_OBJECT_PARAMETER);
        methodBuilder.addStatement("return 0");
        methodBuilder.endControlFlow();

        if(Objects.requireNonNull(objectElement).type() == Type.ENUM) {
            writeEnumCalculator(methodBuilder);
        }else {
            writeMessageCalculator(classBuilder, methodBuilder);
        }
    }

    private void writeEnumCalculator(MethodSpec.Builder methodBuilder) {
        var metadata = Objects.requireNonNull(objectElement).enumMetadata()
                .orElseThrow(() -> new NoSuchElementException("Missing metadata from enum"));
        if(metadata.isJavaEnum()) {
            methodBuilder.addStatement("return ProtobufOutputStream.getVarIntSize($L.ordinal())", INPUT_OBJECT_PARAMETER);
        }else {
            var fieldName = metadata.field()
                    .getSimpleName();
            methodBuilder.addStatement("return ProtobufOutputStream.getVarIntSize($L.$L)", INPUT_OBJECT_PARAMETER, fieldName);
        }
    }

    private void writeMessageCalculator(TypeSpec.Builder classBuilder, MethodSpec.Builder methodBuilder) {
        methodBuilder.addStatement("var $L = 0", OUTPUT_SIZE_NAME);
        if(Objects.requireNonNull(objectElement).type() == Type.GROUP) {
            methodBuilder.addStatement("$L += ProtobufOutputStream.getFieldSize($L, $L)", OUTPUT_SIZE_NAME, GROUP_INDEX_PARAMETER, "ProtobufWireType.WIRE_TYPE_START_OBJECT");
            methodBuilder.addStatement("$L += ProtobufOutputStream.getFieldSize($L, $L)", OUTPUT_SIZE_NAME, GROUP_INDEX_PARAMETER, "ProtobufWireType.WIRE_TYPE_END_OBJECT");
        }

        for(var property : objectElement.properties()) {
            switch (property.type()) {
                case ProtobufPropertyType.CollectionType collectionType -> writeRepeatedSize(
                        methodBuilder,
                        property.index(),
                        property.name(),
                        getAccessorCall(property.accessor()),
                        property.packed(),
                        collectionType,
                        false
                );
                case ProtobufPropertyType.MapType mapType -> writeMapSize(
                        classBuilder,
                        methodBuilder,
                        property.index(),
                        property.name(),
                        getAccessorCall(property.accessor()),
                        mapType,
                        false
                );
                case ProtobufPropertyType.NormalType ignored -> writeNormalSize(
                        methodBuilder,
                        property
                );
            }
        }

        methodBuilder.addStatement("return $L", OUTPUT_SIZE_NAME);
    }

    @Override
    protected List<TypeName> parametersTypes() {
        var objectType = ClassName.get(objectElement.typeElement());
        if(objectElement.type() == Type.GROUP) {
            return List.of(TypeName.INT, objectType);
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

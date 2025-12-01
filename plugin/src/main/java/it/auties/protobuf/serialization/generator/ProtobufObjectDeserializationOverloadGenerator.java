package it.auties.protobuf.serialization.generator;

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import it.auties.protobuf.serialization.model.ProtobufObjectElement;
import it.auties.protobuf.serialization.model.ProtobufObjectElement.Type;

import java.util.List;

// Generates a convenience overload method that deserializes a protobuf object from a byte array
//
// Example Input (Message):
//   @ProtobufMessage
//   public record Person(String name, int age) {}
//
// Example Output (Message):
//   public static Person decode(byte[] protoInputObject) {
//       if (protoInputObject == null) {
//           return null;
//       }
//       return decode(ProtobufInputStream.fromBytes(protoInputObject, 0, protoInputObject.length));
//   }
//
// Example Input (Enum):
//   @ProtobufEnum
//   public enum Status { ACTIVE, INACTIVE }
//
// Example Output (Enum):
//   public static Status decode(Integer protoEnumIndex) {
//       return decode(protoEnumIndex, null);
//   }
//
// Execution Flow:
//   For Messages/Groups:
//     1. Check if byte array is null, return null if so
//     2. Create ProtobufInputStream from byte array
//     3. Delegate to main decode(stream) method
//   For Enums:
//     1. Directly call main decode(index, defaultValue) with null default
public class ProtobufObjectDeserializationOverloadGenerator extends ProtobufDeserializationGenerator {
    private static final String INPUT_OBJECT_PARAMETER = "protoInputObject";
    private static final String ENUM_INDEX_PARAMETER = "protoEnumIndex";
    private static final String GROUP_INDEX_PARAMETER = "protoGroupIndex";

    public ProtobufObjectDeserializationOverloadGenerator(ProtobufObjectElement element) {
        super(element);
    }

    @Override
    protected void doInstrumentation(TypeSpec.Builder classBuilder, MethodSpec.Builder methodBuilder) {
        if(objectElement.type() == Type.ENUM) {
            methodBuilder.addStatement("return $L($L, null)", name(), ENUM_INDEX_PARAMETER);
            return;
        }

        // Check if the input is null
        methodBuilder.beginControlFlow("if ($L == null)", INPUT_OBJECT_PARAMETER);
        methodBuilder.addStatement("return null");
        methodBuilder.endControlFlow();

        // Return the result
        if(objectElement.type() == Type.GROUP) {
            methodBuilder.addStatement("return $L($L, ProtobufInputStream.fromBytes($L, 0, $L.length))", name(), GROUP_INDEX_PARAMETER, INPUT_OBJECT_PARAMETER, INPUT_OBJECT_PARAMETER);
        }else {
            methodBuilder.addStatement("return $L(ProtobufInputStream.fromBytes($L, 0, $L.length))", name(), INPUT_OBJECT_PARAMETER, INPUT_OBJECT_PARAMETER);
        }
    }

    @Override
    public boolean shouldInstrument() {
        return true;
    }

    @Override
    protected TypeName returnType() {
        return ClassName.get(objectElement.typeElement());
    }

    @Override
    protected List<TypeName> parametersTypes() {
        if(objectElement.type() == Type.GROUP) {
            return List.of(TypeName.INT, ArrayTypeName.of(TypeName.BYTE));
        }else if(objectElement.type() == Type.ENUM) {
            return List.of(ClassName.get(Integer.class));
        }else {
            return List.of(ArrayTypeName.of(TypeName.BYTE));
        }
    }

    @Override
    protected List<String> parametersNames() {
        return switch (objectElement.type()) {
            case GROUP -> List.of(GROUP_INDEX_PARAMETER, INPUT_OBJECT_PARAMETER);
            case ENUM -> List.of(ENUM_INDEX_PARAMETER);
            case MESSAGE -> List.of(INPUT_OBJECT_PARAMETER);
        };
    }
}

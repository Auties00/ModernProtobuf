package it.auties.protobuf.serialization.generator;

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import it.auties.protobuf.serialization.model.ProtobufObjectElement;
import it.auties.protobuf.serialization.model.ProtobufObjectElement.Type;

import javax.lang.model.element.Modifier;
import java.util.List;

// Generates a convenience overload method that serializes a protobuf object to a byte array
//
// Example Input:
//   @ProtobufMessage
//   public record Person(String name, int age) {}
//
// Example Output:
//   public static byte[] encode(Person protoInputObject) {
//       if (protoInputObject == null) {
//           return null;
//       }
//       var stream = ProtobufOutputStream.toBytes(sizeOf(protoInputObject));
//       encode(protoInputObject, stream);
//       return stream.toOutput();
//   }
//
// Execution Flow:
//   1. Check if input is null, return null early if so
//   2. Calculate the size needed for serialization using sizeOf()
//   3. Create an output stream with pre-allocated size
//   4. Call the main encode(object, stream) method to serialize
//   5. Convert stream to byte array and return
public class ProtobufObjectSerializationOverloadGenerator extends ProtobufMethodGenerator {
    private static final String INPUT_OBJECT_PARAMETER = "protoInputObject";
    private static final String GROUP_INDEX_PARAMETER = "protoGroupIndex";

    public ProtobufObjectSerializationOverloadGenerator(ProtobufObjectElement element) {
        super(element);
    }

    @Override
    protected void doInstrumentation(TypeSpec.Builder classBuilder, MethodSpec.Builder methodBuilder) {
        // Check if the input is null
        methodBuilder.beginControlFlow("if ($L == null)", INPUT_OBJECT_PARAMETER);
        methodBuilder.addStatement("return null");
        methodBuilder.endControlFlow();

        // Return the result
        if(objectElement.type() == Type.GROUP) {
            methodBuilder.addStatement("var stream = ProtobufOutputStream.toBytes($L($L, $L))", ProtobufObjectSizeGenerator.METHOD_NAME, GROUP_INDEX_PARAMETER, INPUT_OBJECT_PARAMETER);
            methodBuilder.addStatement("encode($L, $L, stream)", GROUP_INDEX_PARAMETER, INPUT_OBJECT_PARAMETER);
        }else {
            methodBuilder.addStatement("var stream = ProtobufOutputStream.toBytes($L($L))", ProtobufObjectSizeGenerator.METHOD_NAME, INPUT_OBJECT_PARAMETER);
            methodBuilder.addStatement("encode($L, stream)", INPUT_OBJECT_PARAMETER);
        }

        methodBuilder.addStatement("return stream.toOutput()");
    }

    @Override
    public boolean shouldInstrument() {
        return objectElement.type() != Type.ENUM;
    }

    @Override
    protected List<Modifier> modifiers() {
        return List.of(Modifier.PUBLIC, Modifier.STATIC);
    }

    @Override
    protected TypeName returnType() {
        return objectElement.type() == Type.ENUM ? ClassName.get(Integer.class) : ArrayTypeName.of(TypeName.BYTE);
    }

    @Override
    public String name() {
        return "encode";
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
}

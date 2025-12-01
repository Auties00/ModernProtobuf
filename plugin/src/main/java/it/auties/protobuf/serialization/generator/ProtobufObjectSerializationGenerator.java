package it.auties.protobuf.serialization.generator;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import it.auties.protobuf.serialization.model.ProtobufObjectElement;
import it.auties.protobuf.serialization.model.ProtobufObjectElement.Type;
import it.auties.protobuf.serialization.model.ProtobufPropertyElement;
import it.auties.protobuf.serialization.model.ProtobufPropertyType;
import it.auties.protobuf.stream.ProtobufOutputStream;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import java.util.List;
import java.util.NoSuchElementException;

// Generates the main serialization method that writes a protobuf object to an output stream
//
// Example Input:
//   @ProtobufMessage
//   public record Person(
//       @ProtobufProperty(index = 1) String name,
//       @ProtobufProperty(index = 2) int age
//   ) {}
//
// Example Output:
//   public static void encode(Person protoInputObject, ProtobufOutputStream protoOutputStream) {
//       if (protoInputObject == null) {
//           return;
//       }
//       Objects.requireNonNull(protoInputObject.name(), "Missing required property: name");
//       var name = protoInputObject.name();
//       if (name != null) {
//           protoOutputStream.writeString(1, name);
//       }
//       var age = protoInputObject.age();
//       protoOutputStream.writeInt32(2, age);
//   }
//
// For Enums:
//   Input: @ProtobufEnum enum Status { ACTIVE, INACTIVE }
//   Output:
//     public static Integer encode(Status protoInputObject) {
//         if (protoInputObject == null) { return null; }
//         return protoInputObject.ordinal();
//     }
//
// Execution Flow:
//   1. Return early if input is null
//   2. For enums: return the ordinal or custom field value
//   3. For messages/groups:
//      a. Write group start marker (if group type)
//      b. Validate required properties are not null
//      c. Iterate through each property and serialize based on type:
//         - Normal fields: write directly to stream
//         - Repeated fields: iterate and write each element
//         - Map fields: iterate entries and write key-value pairs
//      d. Write group end marker (if group type)
public class ProtobufObjectSerializationGenerator extends ProtobufSerializationGenerator {
    private static final String INPUT_OBJECT_PARAMETER = "protoInputObject";
    private static final String OUTPUT_OBJECT_PARAMETER = "protoOutputStream";
    private static final String GROUP_INDEX_PARAMETER = "protoGroupIndex";

    public ProtobufObjectSerializationGenerator(ProtobufObjectElement element) {
        super(element);
    }

    @Override
    protected void doInstrumentation(TypeSpec.Builder classBuilder, MethodSpec.Builder methodBuilder) {
        if (objectElement.type() == Type.ENUM) {
            createEnumSerializer(methodBuilder);
        } else {
            createMessageSerializer(methodBuilder);
        }
    }

    @Override
    public boolean shouldInstrument() {
        return true;
    }

    @Override
    protected List<Modifier> modifiers() {
        return List.of(Modifier.PUBLIC, Modifier.STATIC);
    }

    @Override
    protected TypeName returnType() {
        return objectElement.type() == Type.ENUM ? ClassName.get(Integer.class) : TypeName.VOID;
    }

    @Override
    protected List<TypeName> parametersTypes() {
        var objectType = ClassName.get(objectElement.typeElement());
        if (objectElement.type() == Type.ENUM) {
            return List.of(objectType);
        }else if(objectElement.type() == Type.GROUP) {
            return List.of(TypeName.INT, objectType, ClassName.get(ProtobufOutputStream.class));
        }else {
            return List.of(objectType, ClassName.get(ProtobufOutputStream.class));
        }
    }

    @Override
    protected List<String> parametersNames() {
        if (objectElement.type() == Type.ENUM) {
            return List.of(INPUT_OBJECT_PARAMETER);
        }else if(objectElement.type() == Type.GROUP) {
            return List.of(GROUP_INDEX_PARAMETER, INPUT_OBJECT_PARAMETER, OUTPUT_OBJECT_PARAMETER);
        }else {
            return List.of(INPUT_OBJECT_PARAMETER, OUTPUT_OBJECT_PARAMETER);
        }
    }

    private void createEnumSerializer(MethodSpec.Builder methodBuilder) {
        methodBuilder.beginControlFlow("if ($L == null)", INPUT_OBJECT_PARAMETER);
        methodBuilder.addStatement("return null");
        methodBuilder.endControlFlow();

        var metadata = objectElement.enumMetadata()
                .orElseThrow(() -> new NoSuchElementException("Missing metadata from enum"));
        if(metadata.isJavaEnum()) {
            methodBuilder.addStatement("return $L.ordinal()", INPUT_OBJECT_PARAMETER);
        }else {
            var fieldName = metadata.field().getSimpleName();
            methodBuilder.addStatement("return $L.$L", INPUT_OBJECT_PARAMETER, fieldName);
        }
    }

    private void createMessageSerializer(MethodSpec.Builder methodBuilder) {
        methodBuilder.beginControlFlow("if ($L == null)", INPUT_OBJECT_PARAMETER);
        methodBuilder.addStatement("return");
        methodBuilder.endControlFlow();

        if(objectElement.type() == Type.GROUP) {
            methodBuilder.addStatement("$L.writeGroupStart($L)", OUTPUT_OBJECT_PARAMETER, GROUP_INDEX_PARAMETER);
        }

        createRequiredPropertiesNullCheck(methodBuilder);
        for(var property : objectElement.properties()) {
            switch (property.type()) {
                case ProtobufPropertyType.CollectionType collectionType -> writeRepeatedSerializer(methodBuilder, property.index(), property.name(), getAccessorCall(property.accessor()), collectionType, property.packed(), true, false);
                case ProtobufPropertyType.MapType mapType -> writeMapSerializer(methodBuilder, property.index(), property.name(), getAccessorCall(property.accessor()), mapType);
                default -> writeNormalSerializer(methodBuilder, property.index(), property.name(), getAccessorCall(property.accessor()), property.type(), true, true, false);
            }
        }

        if(objectElement.type() == Type.GROUP) {
            methodBuilder.addStatement("$L.writeGroupEnd($L)", OUTPUT_OBJECT_PARAMETER, GROUP_INDEX_PARAMETER);
        }
    }

    private void createRequiredPropertiesNullCheck(MethodSpec.Builder methodBuilder) {
        objectElement.properties()
                .stream()
                .filter(ProtobufPropertyElement::required)
                .forEach(entry -> methodBuilder.addStatement("Objects.requireNonNull($L, $S)", getAccessorCall(entry.accessor()), "Missing required property: " + entry.name()));
    }

    private String getAccessorCall(Element accessor) {
        return getAccessorCall(INPUT_OBJECT_PARAMETER, accessor);
    }
}

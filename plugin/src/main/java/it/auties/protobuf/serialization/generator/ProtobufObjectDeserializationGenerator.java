package it.auties.protobuf.serialization.generator;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import it.auties.protobuf.exception.ProtobufDeserializationException;
import it.auties.protobuf.serialization.model.ProtobufObjectElement;
import it.auties.protobuf.serialization.model.ProtobufObjectElement.Type;
import it.auties.protobuf.serialization.model.ProtobufPropertyElement;
import it.auties.protobuf.serialization.model.ProtobufPropertyType;
import it.auties.protobuf.serialization.model.ProtobufReservedElement;
import it.auties.protobuf.io.ProtobufReader;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.List;

// Generates the main deserialization method that reads a protobuf object from an input stream
//
// Example Input:
//   @ProtobufMessage
//   public record Person(
//       @ProtobufProperty(index = 1) String name,
//       @ProtobufProperty(index = 2) int age
//   ) {}
//
// Example Output:
//   public static Person decode(ProtobufInputStream protoInputStream) {
//       String name = null;
//       int age = 0;
//       while (protoInputStream.readTag()) {
//           var protoFieldIndex = protoInputStream.index();
//           switch (protoFieldIndex) {
//               case 1L:
//                   name = protoInputStream.readString();
//                   break;
//               case 2L:
//                   age = protoInputStream.readInt32();
//                   break;
//               default:
//                   protoInputStream.skipUnknown();
//                   break;
//           }
//       }
//       Objects.requireNonNull(name, "Missing required property: name");
//       return new Person(name, age);
//   }
//
// For Enums:
//   Input: @ProtobufEnum enum Status { ACTIVE, INACTIVE }
//   Output:
//     public static Status decode(Integer protoEnumIndex, Status defaultValue) {
//         return VALUES.getOrDefault(protoEnumIndex, defaultValue);
//     }
//
// Execution Flow:
//   1. For enums: Look up value by index in pre-built VALUES map
//   2. For messages/groups:
//      a. Assert group is opened (if group type)
//      b. Declare variables for all properties with default values
//      c. Read tags from stream in a while loop
//      d. For each tag, get the field index
//      e. Check if index is reserved, throw exception if so
//      f. Switch on field index to deserialize appropriate field:
//         - Normal fields: read directly from stream
//         - Repeated fields: add to collection
//         - Map fields: read key-value pair and add to map
//         - Unknown fields: skip or store if unknown fields handler exists
//      g. Assert group is closed (if group type)
//      h. Validate all required fields are non-null
//      i. Construct and return object using deserialized values
public class ProtobufObjectDeserializationGenerator extends ProtobufDeserializationGenerator {
    private static final String INPUT_STREAM_NAME = "protoInputStream";
    private static final String GROUP_INDEX_PARAMETER = "protoGroupIndex";
    private static final String ENUM_INDEX_PARAMETER = "protoEnumIndex";
    private static final String DEFAULT_UNKNOWN_FIELDS = "protoUnknownFields";
    private static final String FIELD_INDEX_VARIABLE = "protoFieldIndex";
    private static final String ENUM_DEFAULT_VALUE_PARAMETER = "defaultValue";
    public static final String ENUM_VALUES_FIELD = "VALUES";

    public ProtobufObjectDeserializationGenerator(ProtobufObjectElement element) {
        super(element);
    }

    @Override
    protected void doInstrumentation(TypeSpec.Builder classBuilder, MethodSpec.Builder methodBuilder) {
        if (objectElement.type() == Type.ENUM) {
            createEnumDeserializer(methodBuilder);
        }else {
            createMessageDeserializer(methodBuilder);
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
        if(objectElement.type() == Type.ENUM) {
            return List.of(ClassName.get(Integer.class), ClassName.get(objectElement.typeElement()));
        } else if(objectElement.type() == Type.GROUP) {
            return List.of(TypeName.LONG, ClassName.get(ProtobufReader.class));
        } else {
            return List.of(ClassName.get(ProtobufReader.class));
        }
    }

    @Override
    protected List<String> parametersNames() {
        if(objectElement.type() == Type.ENUM) {
            return List.of(ENUM_INDEX_PARAMETER, ENUM_DEFAULT_VALUE_PARAMETER);
        } else if(objectElement.type() == Type.GROUP) {
            return List.of(GROUP_INDEX_PARAMETER, INPUT_STREAM_NAME);
        } else {
            return List.of(INPUT_STREAM_NAME);
        }
    }

    private void checkPropertyIndex(MethodSpec.Builder methodBuilder, String indexField) {
        var conditions = new ArrayList<String>();
        for(var index : objectElement.reservedElements()) {
            switch (index) {
                case ProtobufReservedElement.Index.Range range -> conditions.add("(%s >= %s && %s <= %s)".formatted(indexField, range.min(), indexField, range.max()));
                case ProtobufReservedElement.Index.Value entry -> conditions.add("%s == %s".formatted(indexField, entry.value()));
                case ProtobufReservedElement.Name ignored -> {} // TODO: Is this right?
            }
        }
        if(!conditions.isEmpty()) {
            methodBuilder.beginControlFlow("if ($L)", String.join(" || ", conditions));
            methodBuilder.addStatement("throw $T.reservedIndex($L)", ProtobufDeserializationException.class, indexField);
            methodBuilder.endControlFlow();
        }
    }

    private void createEnumDeserializer(MethodSpec.Builder methodBuilder) {
        checkPropertyIndex(methodBuilder, ENUM_INDEX_PARAMETER);
        methodBuilder.addStatement("return $L.getOrDefault($L, $L)", ENUM_VALUES_FIELD, ENUM_INDEX_PARAMETER, ENUM_DEFAULT_VALUE_PARAMETER);
    }

    private void createMessageDeserializer(MethodSpec.Builder methodBuilder) {
        if(objectElement.type() == Type.GROUP) {
            methodBuilder.addStatement("$L.assertGroupOpened($L)", INPUT_STREAM_NAME, GROUP_INDEX_PARAMETER);
        }

        // Declare all variables
        // [<implementationType> var<index> = <defaultValue>, ...]
        for(var property : objectElement.properties()) {
            if(property.synthetic()) {
                continue;
            }

            var propertyType = property.type().descriptorElementType().toString();
            var propertyName = property.name();
            var propertyDefaultValue = property.type().descriptorDefaultValue();
            methodBuilder.addStatement("$L $L = $L", propertyType, propertyName, propertyDefaultValue);
        }

        // Declare the unknown fields valueType if needed
        objectElement.unknownFieldsElement()
                .ifPresent(unknownFieldsElement -> methodBuilder.addStatement("$L $L = $L", unknownFieldsElement.type().toString(), DEFAULT_UNKNOWN_FIELDS, unknownFieldsElement.defaultValue()));

        // Write deserializer implementation
        var argumentsList = new ArrayList<String>();
        methodBuilder.beginControlFlow("while ($L.readTag())", INPUT_STREAM_NAME);
        methodBuilder.addStatement("var $L = $L.index()", FIELD_INDEX_VARIABLE, INPUT_STREAM_NAME);
        checkPropertyIndex(methodBuilder, FIELD_INDEX_VARIABLE);

        var switchCases = new ArrayList<CodeBlock>();
        var switchIndexes = new ArrayList<String>();
        for(var property : objectElement.properties()) {
            if(property.synthetic()) {
                continue;
            }

            switchIndexes.add(property.index() + "L");
            var branch = switch (property.type()) {
                case ProtobufPropertyType.MapType mapType -> writeMapDeserializer(property.name(), mapType);
                case ProtobufPropertyType.CollectionType collectionType -> writeDeserializer(property.name(), collectionType.valueType(), true, property.packed());
                default -> writeDeserializer(property.name(), property.type(), false, property.packed());
            };
            switchCases.add(branch);

            argumentsList.add(property.name());
        }

        var defaultCase = writeDefaultPropertyDeserializer();
        switchIndexes.add("default");
        switchCases.add(defaultCase);

        methodBuilder.beginControlFlow("switch ($L)", FIELD_INDEX_VARIABLE);
        for (int i = 0; i < switchIndexes.size(); i++) {
            var caseLabel = CodeBlock.builder()
                    .add("case $L:\n", switchIndexes.get(i))
                    .indent()
                    .add(switchCases.get(i))
                    .unindent()
                    .build();
            methodBuilder.addCode(caseLabel);
        }
        methodBuilder.endControlFlow();
        methodBuilder.endControlFlow();

        if(objectElement.type() == Type.GROUP) {
            methodBuilder.addStatement("$L.assertGroupClosed($L)", INPUT_STREAM_NAME, GROUP_INDEX_PARAMETER);
        }

        // Null check required properties
        objectElement.properties()
                .stream()
                .filter(ProtobufPropertyElement::required)
                .forEach(entry -> checkRequiredProperty(methodBuilder, entry));

        // Return statement
        var unknownFieldsArg = objectElement.unknownFieldsElement().isEmpty() ? "" : ", " + DEFAULT_UNKNOWN_FIELDS;
        if(objectElement.deserializer().isPresent()) {
            methodBuilder.addStatement("return $L.$L($L$L)", objectElement.typeElement().getQualifiedName(), objectElement.deserializer().get().name(), String.join(", ", argumentsList), unknownFieldsArg);
        }else {
            methodBuilder.addStatement("return new $L($L$L)", objectElement.typeElement().getQualifiedName(), String.join(", ", argumentsList), unknownFieldsArg);
        }
    }

    private CodeBlock writeDefaultPropertyDeserializer() {
        var caseBlock = CodeBlock.builder();
        var unknownFieldsElement = objectElement.unknownFieldsElement()
                .orElse(null);
        if(unknownFieldsElement == null) {
            caseBlock.addStatement("$L.skipUnknown()", INPUT_STREAM_NAME);
            caseBlock.addStatement("break");
            return caseBlock.build();
        }

        var setter = unknownFieldsElement.setter();
        var value = "%s.readUnknown()".formatted(INPUT_STREAM_NAME);
        if(setter.getModifiers().contains(Modifier.STATIC)) {
            var setterWrapperClass = (TypeElement) setter.getEnclosingElement();
            caseBlock.addStatement("$L.$L($L, $L, $L)", setterWrapperClass.getQualifiedName(), setter.getSimpleName(), DEFAULT_UNKNOWN_FIELDS, FIELD_INDEX_VARIABLE, value);
        }else {
            caseBlock.addStatement("$L.$L($L, $L)", DEFAULT_UNKNOWN_FIELDS, setter.getSimpleName(), FIELD_INDEX_VARIABLE, value);
        }
        caseBlock.addStatement("break");
        return caseBlock.build();
    }

    private void checkRequiredProperty(MethodSpec.Builder methodBuilder, ProtobufPropertyElement property) {
        if (!(property.type() instanceof ProtobufPropertyType.CollectionType)) {
            methodBuilder.addStatement("Objects.requireNonNull($L, $S)", property.name(), "Missing required property: " + property.name());
            return;
        }

        methodBuilder.beginControlFlow("if (!$L.isEmpty())", property.name());
        methodBuilder.addStatement("throw new NullPointerException($S)", "Missing required property: " + property.name());
        methodBuilder.endControlFlow();
    }
}

package it.auties.protobuf.serialization.generator.method;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.serialization.object.ProtobufObjectElement;
import it.auties.protobuf.serialization.property.ProtobufPropertyElement;
import it.auties.protobuf.serialization.property.ProtobufPropertyType;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter.SwitchStatementWriter;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public class ProtobufDeserializationMethodGenerator extends ProtobufMethodGenerator {
    private static final String DEFAULT_STREAM_NAME = "protoInputStream";
    private static final String DEFAULT_UNKNOWN_FIELDS = "protoUnknownFields";
    private static final String DEFAULT_INDEX_NAME = "protoFieldIndex";

    public ProtobufDeserializationMethodGenerator(ProtobufObjectElement element) {
        super(element);
    }

    @Override
    protected void doInstrumentation(ClassWriter classWriter, ClassWriter.MethodWriter writer) {
        if (message.isEnum()) {
            createEnumDeserializer(writer);
        }else {
            createMessageDeserializer(writer);
        }
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
    protected String name() {
        return "decode";
    }

    @Override
    protected String returnType() {
        return message.isEnum() ? "Optional<%s>".formatted(message.element().getSimpleName())
                : message.element().getSimpleName().toString();
    }

    @Override
    protected List<String> parametersTypes() {
        return message.isEnum() ? List.of("int") : List.of("byte[]");
    }

    @Override
    protected List<String> parametersNames() {
        return message.isEnum() ? List.of("index") : List.of("input");
    }

    private void createEnumDeserializer(ClassWriter.MethodWriter writer) {
        var fieldName = message.enumMetadata()
                .orElseThrow(() -> new NoSuchElementException("Missing metadata from enum"))
                .field()
                .getSimpleName();
        writer.println("return Arrays.stream(%s.values())".formatted(message.element().getSimpleName()));
        writer.println("        .filter(entry -> entry.%s == index)".formatted(fieldName));
        writer.println("        .findFirst();");
    }

    private void createMessageDeserializer(ClassWriter.MethodWriter methodWriter) {
        // Check if the input is null
        try(var ifWriter = methodWriter.printIfStatement("input == null")) {
            ifWriter.printReturn("null");
        }

        // Initialize a ProtobufInputStream from the input
        methodWriter.printVariableDeclaration(DEFAULT_STREAM_NAME, "new ProtobufInputStream(input)");

        // Declare all variables
        // [<implementationType> var<index> = <defaultValue>, ...]
        for(var property : message.properties()) {
            var propertyType = property.type().descriptorElementType().toString();
            var propertyName = property.name();
            var propertyDefaultValue = property.type().defaultValue();
            methodWriter.printVariableDeclaration(propertyType, propertyName, propertyDefaultValue);
        }

        // Declare the unknown fields value if needed
        message.unknownFieldsElement()
                .ifPresent(unknownFieldsElement -> methodWriter.printVariableDeclaration(unknownFieldsElement.type().toString(), DEFAULT_UNKNOWN_FIELDS, unknownFieldsElement.defaultValue()));

        // Write deserializer implementation
        var argumentsList = new ArrayList<String>();
        try(var whileWriter = methodWriter.printWhileStatement(DEFAULT_STREAM_NAME + ".readTag()")) {
            whileWriter.printVariableDeclaration(DEFAULT_INDEX_NAME, DEFAULT_STREAM_NAME + ".index()");
            try(var switchWriter = whileWriter.printSwitchStatement(DEFAULT_INDEX_NAME)) {
                for(var property : message.properties()) {
                    switch (property.type()) {
                        case ProtobufPropertyType.MapType mapType -> writeMapSerializer(switchWriter, property, mapType);
                        case ProtobufPropertyType.CollectionType collectionType -> writeDeserializer(switchWriter, property.name(), property.index(), collectionType.value(), true, property.packed());
                        default -> writeDeserializer(switchWriter, property.name(), property.index(), property.type(), false, property.packed());
                    }
                    argumentsList.add(property.name());
                }
                writeDefaultPropertyDeserializer(switchWriter);
            }
        }

        // Null check required properties
        message.properties()
                .stream()
                .filter(ProtobufPropertyElement::required)
                .forEach(entry -> checkRequiredProperty(methodWriter, entry));

        // Return statement
        var unknownFieldsArg = message.unknownFieldsElement().isEmpty() ? "" : ", " + DEFAULT_UNKNOWN_FIELDS;
        if(message.deserializer().isPresent()) {
            methodWriter.printReturn("%s.%s(%s%s)".formatted(message.element().getQualifiedName(), message.deserializer().get().getSimpleName(), String.join(", ", argumentsList), unknownFieldsArg));
        }else {
            methodWriter.printReturn("new %s(%s%s)".formatted(message.element().getQualifiedName(), String.join(", ", argumentsList), unknownFieldsArg));
        }
    }

    private void writeDefaultPropertyDeserializer(SwitchStatementWriter switchWriter) {
        var unknownFieldsElement = message.unknownFieldsElement()
                .orElse(null);
        if(unknownFieldsElement == null) {
            switchWriter.printSwitchBranch("default", "%s.skipBytes()".formatted(DEFAULT_STREAM_NAME));
            return;
        }

        var setter = unknownFieldsElement.setter();
        var value = "%s.readUnknown()".formatted(DEFAULT_STREAM_NAME);
        if(setter.getModifiers().contains(Modifier.STATIC)) {
            var setterWrapperClass = (TypeElement) setter.getEnclosingElement();
            switchWriter.printSwitchBranch("default", "%s.%s(%s, %s, %s)".formatted(setterWrapperClass.getQualifiedName(), setter.getSimpleName(), DEFAULT_UNKNOWN_FIELDS, DEFAULT_INDEX_NAME, value));
        }else {
            switchWriter.printSwitchBranch("default", "%s.%s(%s, %s)".formatted(DEFAULT_UNKNOWN_FIELDS, setter.getSimpleName(), DEFAULT_INDEX_NAME, value));
        }
    }

    private void writeMapSerializer(SwitchStatementWriter writer, ProtobufPropertyElement property, ProtobufPropertyType.MapType mapType) {
        try(var switchBranchWriter = writer.printSwitchBranch(String.valueOf(property.index()))) {
            var streamName = "%sInputStream".formatted(property.name());
            var keyName = "%sKey".formatted(property.name());
            var valueName = "%sValue".formatted(property.name());
            switchBranchWriter.printVariableDeclaration(streamName, "new ProtobufInputStream(%s.readBytes())".formatted(DEFAULT_STREAM_NAME));
            switchBranchWriter.printVariableDeclaration(mapType.keyType().accessorType().toString(), keyName, "null");
            switchBranchWriter.printVariableDeclaration(mapType.valueType().accessorType().toString(), valueName, "null");
            var keyReadMethod = getDeserializerStreamMethod(mapType.keyType(), false);
            var keyReadFunction = getConvertedValue(streamName, mapType.keyType(), keyReadMethod);
            var valueReadMethod = getDeserializerStreamMethod(mapType.valueType(), false);
            var valueReadFunction = getConvertedValue(streamName, mapType.valueType(), valueReadMethod);
            try(var whileWriter = switchBranchWriter.printWhileStatement(streamName + ".readTag()")) {
                try(var mapSwitchWriter = whileWriter.printSwitchStatement(streamName + ".index()")) {
                    mapSwitchWriter.printSwitchBranch("1", "%s = %s".formatted(keyName, keyReadFunction));
                    mapSwitchWriter.printSwitchBranch("2", "%s = %s".formatted(valueName, valueReadFunction));
                }
            }
            switchBranchWriter.println("%s.put(%s, %s);".formatted(property.name(), keyName, valueName));
        }
    }

    private void writeDeserializer(SwitchStatementWriter writer, String name, int index, ProtobufPropertyType type, boolean repeated, boolean packed) {
        var readMethod = getDeserializerStreamMethod(type, packed);
        var readFunction = getConvertedValue(DEFAULT_STREAM_NAME, type, readMethod);
        var readAssignment = getReadAssignment(name, repeated, packed, readFunction);
        writer.printSwitchBranch(String.valueOf(index), readAssignment);
    }

    private void checkRequiredProperty(ClassWriter.MethodWriter writer, ProtobufPropertyElement property) {
        if (!(property.type() instanceof ProtobufPropertyType.CollectionType)) {
            writer.println("Objects.requireNonNull(%s, \"Missing required property: %s\");".formatted(property.name(), property.name()));
            return;
        }

        try(var ifWriter = writer.printIfStatement("!%s.isEmpty()".formatted(property.name()))) {
            ifWriter.println("throw new NullPointerException(\"Missing required property: %s\");".formatted(property.name()));
        }
    }

    private String getReadAssignment(String name, boolean repeated, boolean packed, String readFunction) {
        if (!repeated) {
            return "%s = %s".formatted(name, readFunction);
        }

        var repeatedMethod = packed ? "addAll" : "add";
        return "%s.%s(%s)".formatted(name, repeatedMethod, readFunction);
    }

    private String getConvertedValue(String streamName, ProtobufPropertyType implementation, String readMethod) {
        var result = "%s.%s()".formatted(streamName, readMethod);
        if(implementation.protobufType() == ProtobufType.OBJECT && implementation instanceof ProtobufPropertyType.NormalType normalType) {
            var elementType = (DeclaredType) implementation.deserializedType();
            var elementSpecName = getSpecFromObject(elementType);
            if(elementType.asElement().getKind() == ElementKind.ENUM) {
                var defaultValue = normalType.deserializedDefaultValue()
                        .orElseGet(normalType::defaultValue);
                result = "%s.decode(%s).orElse(%s)".formatted(elementSpecName, result, defaultValue);
            } else {
                result = "%s.decode(%s)".formatted(elementSpecName, result);
            }
        }

        var deserializers = implementation.deserializers();
        for (var i = deserializers.size() - 1; i >= 0; i--) {
            var converter = deserializers.get(i);
            var converterWrapperClass = (TypeElement) converter.delegate().getEnclosingElement();
            var converterMethodName = converter.delegate().getSimpleName();
            result = "%s.%s(%s)".formatted(converterWrapperClass.getQualifiedName(), converterMethodName, result);
        }

        return result;
    }

    // Returns the method to use to deserialize a property from ProtobufInputStream
    private String getDeserializerStreamMethod(ProtobufPropertyType type, boolean packed) {
        if (isEnum(type)) {
            return packed ? "readInt32Packed" : "readInt32";
        }
        
        return switch (type.protobufType()) {
            case STRING -> "readString";
            case OBJECT, BYTES -> "readBytes";
            case BOOL -> packed ? "readBoolPacked" : "readBool";
            case INT32, SINT32, UINT32 -> packed ? "readInt32Packed" : "readInt32";
            case FLOAT -> packed ? "readFloatPacked" : "readFloat";
            case DOUBLE -> packed ? "readDoublePacked" : "readDouble";
            case FIXED32, SFIXED32 -> packed ? "readFixed32Packed" : "readFixed32";
            case INT64, SINT64, UINT64 -> packed ? "readInt64Packed" : "readInt64";
            case FIXED64, SFIXED64 -> packed ? "readFixed64Packed" : "readFixed64";
            default -> throw new IllegalStateException("Unexpected value: " + type.protobufType());
        };
    }

    protected boolean isEnum(ProtobufPropertyType type) {
        return type instanceof ProtobufPropertyType.NormalType normalType
                && normalType.serializedType() instanceof DeclaredType declaredType
                && declaredType.asElement().getKind() == ElementKind.ENUM;
    }
}

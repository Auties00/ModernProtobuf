package it.auties.protobuf.serialization.generator.method;

import it.auties.protobuf.annotation.ProtobufGroup;
import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.serialization.model.object.ProtobufObjectElement;
import it.auties.protobuf.serialization.model.property.ProtobufGroupPropertyElement;
import it.auties.protobuf.serialization.model.property.ProtobufPropertyElement;
import it.auties.protobuf.serialization.model.property.ProtobufPropertyType;
import it.auties.protobuf.serialization.support.JavaWriter.BodyWriter;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter.MethodWriter;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter.SwitchStatementWriter;
import it.auties.protobuf.stream.ProtobufInputStream;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import java.util.*;

public class ProtobufDeserializationMethodGenerator extends ProtobufMethodGenerator {
    private static final String INPUT_STREAM_NAME = "protoInputStream";
    private static final String GROUP_INDEX_PARAMETER = "protoGroupIndex";
    private static final String ENUM_INDEX_PARAMETER = "protoEnumIndex";
    private static final String DEFAULT_UNKNOWN_FIELDS = "protoUnknownFields";
    private static final String FIELD_INDEX_VARIABLE = "protoFieldIndex";
    public static final String ENUM_VALUES_FIELD = "VALUES";

    private final Set<String> deferredGroupDeserializers;
    public ProtobufDeserializationMethodGenerator(ProtobufObjectElement element) {
        super(element);
        this.deferredGroupDeserializers = new HashSet<>();
    }

    @Override
    protected void doInstrumentation(ClassWriter classWriter, MethodWriter writer) {
        if (message.isEnum()) {
            createEnumDeserializer(writer);
        }else {
            createMessageDeserializer(classWriter, writer);
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
        var objectType = message.element().getSimpleName().toString();
        if (message.isEnum()) {
            return "Optional<%s>".formatted(objectType);
        }else {
            return objectType;
        }
    }

    @Override
    protected List<String> parametersTypes() {
        if(message.isEnum()) {
            return List.of("int");
        } else if(message.isGroup()) {
            return List.of("int", ProtobufInputStream.class.getSimpleName());
        } else {
            return List.of(ProtobufInputStream.class.getSimpleName());
        }
    }

    @Override
    protected List<String> parametersNames() {
        if(message.isEnum()) {
            return List.of(ENUM_INDEX_PARAMETER);
        } else if(message.isGroup()) {
            return List.of(GROUP_INDEX_PARAMETER, INPUT_STREAM_NAME);
        } else {
            return List.of(INPUT_STREAM_NAME);
        }
    }

    private void createEnumDeserializer(MethodWriter writer) {
        checkIndex(writer, ENUM_INDEX_PARAMETER);
        writer.printReturn("Optional.ofNullable(%s.get(%s))".formatted(ENUM_VALUES_FIELD, ENUM_INDEX_PARAMETER));
    }

    private void checkIndex(BodyWriter writer, String indexField) {
        var conditions = new ArrayList<String>();
        for(var index : message.reservedIndexes()) {
            switch (index) {
                case ProtobufObjectElement.ReservedIndex.Range range -> conditions.add("(%s >= %s && %s <= %s)".formatted(indexField, range.min(), indexField, range.max()));
                case ProtobufObjectElement.ReservedIndex.Value entry -> conditions.add("%s == %s".formatted(indexField, entry.value()));
            }
        }
        if(!conditions.isEmpty()) {
            try(var illegalIndexCheck = writer.printIfStatement(String.join(" || ", conditions))) {
                illegalIndexCheck.println("throw it.auties.protobuf.exception.ProtobufDeserializationException.reservedIndex(%s);".formatted(indexField));
            }
        }
    }

    private void createMessageDeserializer(ClassWriter classWriter, MethodWriter methodWriter) {
        if(message.isGroup()) {
            methodWriter.println("%s.assertGroupOpened(%s);".formatted(INPUT_STREAM_NAME, GROUP_INDEX_PARAMETER));
        }

        // Declare all variables
        // [<implementationType> var<index> = <defaultValue>, ...]
        for(var property : message.properties()) {
            if(property.synthetic()) {
                continue;
            }

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
        try(var whileWriter = methodWriter.printWhileStatement(INPUT_STREAM_NAME + ".readTag()")) {
            whileWriter.printVariableDeclaration(FIELD_INDEX_VARIABLE, INPUT_STREAM_NAME + ".index()");
            checkIndex(whileWriter, FIELD_INDEX_VARIABLE);
            try(var switchWriter = whileWriter.printSwitchStatement(FIELD_INDEX_VARIABLE)) {
                for(var property : message.properties()) {
                    if(property.synthetic()) {
                        continue;
                    }

                    switch (property.type()) {
                        case ProtobufPropertyType.MapType mapType -> writeMapProperty(switchWriter, property.index(), property.name(), mapType);
                        case ProtobufPropertyType.CollectionType collectionType -> writeAnyProperty(classWriter, switchWriter, property.name(), property.index(), collectionType.value(), true, property.packed(), null);
                        default -> writeAnyProperty(classWriter, switchWriter, property.name(), property.index(), property.type(), false, property.packed(), null);
                    }
                    argumentsList.add(property.name());
                }
                writeDefaultPropertyDeserializer(switchWriter);
            }
        }

        if(message.isGroup()) {
            methodWriter.println("%s.assertGroupClosed(%s);".formatted(INPUT_STREAM_NAME, GROUP_INDEX_PARAMETER));
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
            switchWriter.printSwitchBranch("default", "%s.readUnknown(false)".formatted(INPUT_STREAM_NAME));
            return;
        }

        var setter = unknownFieldsElement.setter();
        var value = "%s.readUnknown(true)".formatted(INPUT_STREAM_NAME);
        if(setter.getModifiers().contains(Modifier.STATIC)) {
            var setterWrapperClass = (TypeElement) setter.getEnclosingElement();
            switchWriter.printSwitchBranch("default", "%s.%s(%s, %s, %s)".formatted(setterWrapperClass.getQualifiedName(), setter.getSimpleName(), DEFAULT_UNKNOWN_FIELDS, FIELD_INDEX_VARIABLE, value));
        }else {
            switchWriter.printSwitchBranch("default", "%s.%s(%s, %s)".formatted(DEFAULT_UNKNOWN_FIELDS, setter.getSimpleName(), FIELD_INDEX_VARIABLE, value));
        }
    }

    private void writeMapProperty(SwitchStatementWriter writer, int index, String name, ProtobufPropertyType.MapType mapType) {
        try(var switchBranchWriter = writer.printSwitchBranch(String.valueOf(index))) {
            var streamName = "%sInputStream".formatted(name);
            switchBranchWriter.printVariableDeclaration(streamName, "%s.lengthDelimitedStream()".formatted(INPUT_STREAM_NAME));
            var keyName = switchBranchWriter.printVariableDeclaration(getQualifiedName(mapType.keyType().accessorType()), "%sKey".formatted(name), "null");
            var valueName = switchBranchWriter.printVariableDeclaration(getQualifiedName(mapType.valueType().accessorType()), "%sValue".formatted(name), "null");
            var keyReadMethod = getDeserializerStreamMethod(mapType.keyType(), false);
            var keyReadFunction = getConvertedValue(index, streamName, mapType.keyType(), false, keyReadMethod);
            var valueReadMethod = getDeserializerStreamMethod(mapType.valueType(), false);
            var valueReadFunction = getConvertedValue(index, streamName, mapType.valueType(), false, valueReadMethod);
            try(var whileWriter = switchBranchWriter.printWhileStatement(streamName + ".readTag()")) {
                try(var mapSwitchWriter = whileWriter.printSwitchStatement(streamName + ".index()")) {
                    mapSwitchWriter.printSwitchBranch("1", "%s = %s".formatted(keyName, keyReadFunction));
                    mapSwitchWriter.printSwitchBranch("2", "%s = %s".formatted(valueName, valueReadFunction));
                }
            }
            switchBranchWriter.println("%s.put(%s, %s);".formatted(name, keyName, valueName));
        }
    }

    private void writeAnyProperty(ClassWriter classWriter, SwitchStatementWriter writer, String name, int index, ProtobufPropertyType type, boolean repeated, boolean packed, String mapTargetName) {
        var readMethod = getDeserializerStreamMethod(type, packed);
        if(isRawGroup(type)) {
            var typeName = getSimpleName(type.deserializedType());
            var methodName = "decode" + typeName;
            if(deferredGroupDeserializers.add(typeName)) {
                deferredOperations.add(() -> createRawGroupDeserializer(classWriter, name, methodName, type));
            }
            var readFunction = getConvertedValue(index, "%s(%s, %s)".formatted(methodName, index, INPUT_STREAM_NAME), type, true, readMethod);
            var readAssignment = getReadAssignment(name, repeated, packed, readFunction, mapTargetName);
            writer.printSwitchBranch(String.valueOf(index), readAssignment);
        }else {
            var readFunction = getConvertedValue(index, INPUT_STREAM_NAME, type, false, readMethod);
            var readAssignment = getReadAssignment(name, repeated, packed, readFunction, mapTargetName);
            writer.printSwitchBranch(String.valueOf(index), readAssignment);
        }
    }

    private void createRawGroupDeserializer(ClassWriter classWriter, String name, String methodName, ProtobufPropertyType type) {
        try (var methodWriter = classWriter.printMethodDeclaration(List.of("private", "static"), "java.util.Map<Integer, Object>", methodName, "int groupIndex, ProtobufInputStream %s".formatted(INPUT_STREAM_NAME))) {
            methodWriter.println("%s.assertGroupOpened(groupIndex);".formatted(INPUT_STREAM_NAME));
            var rawGroupData = methodWriter.printVariableDeclaration(name + "GroupData", "new java.util.HashMap()");
            for(var property :type.serializers().getLast().groupProperties().entrySet()) {
                if(property.getValue().type() instanceof ProtobufPropertyType.CollectionType collectionType) {
                    methodWriter.printVariableDeclaration(getRawGroupCollectionFieldName(name, property), collectionType.defaultValue());
                }
            }

            try(var whileWriter = methodWriter.printWhileStatement(INPUT_STREAM_NAME + ".readTag()")) {
                var index = whileWriter.printVariableDeclaration("index", INPUT_STREAM_NAME + ".index()");
                try(var mapSwitchWriter = whileWriter.printSwitchStatement(index)) {
                    for(var groupProperty : type.serializers().getLast().groupProperties().entrySet()) {
                        var groupPropertyIndex = groupProperty.getValue().index();
                        switch (groupProperty.getValue().type()) {
                            case ProtobufPropertyType.MapType mapType -> writeMapProperty(mapSwitchWriter, groupPropertyIndex, rawGroupData, mapType);
                            case ProtobufPropertyType.CollectionType collectionType -> writeAnyProperty(classWriter, mapSwitchWriter, getRawGroupCollectionFieldName(name, groupProperty), groupPropertyIndex, collectionType.value(), true, groupProperty.getValue().packed(), rawGroupData);
                            default -> writeAnyProperty(classWriter, mapSwitchWriter, rawGroupData, groupPropertyIndex, groupProperty.getValue().type(), false, groupProperty.getValue().packed(), rawGroupData);
                        }
                    }
                }
            }
            methodWriter.println("%s.assertGroupClosed(groupIndex);".formatted(INPUT_STREAM_NAME));
            methodWriter.printReturn(rawGroupData);
        }
    }

    private String getRawGroupCollectionFieldName(String name, Map.Entry<Integer, ProtobufGroupPropertyElement> property) {
        return name + property.getKey();
    }

    private void checkRequiredProperty(MethodWriter writer, ProtobufPropertyElement property) {
        if (!(property.type() instanceof ProtobufPropertyType.CollectionType)) {
            writer.println("Objects.requireNonNull(%s, \"Missing required property: %s\");".formatted(property.name(), property.name()));
            return;
        }

        try(var ifWriter = writer.printIfStatement("!%s.isEmpty()".formatted(property.name()))) {
            ifWriter.println("throw new NullPointerException(\"Missing required property: %s\");".formatted(property.name()));
        }
    }

    private String getReadAssignment(String name, boolean repeated, boolean packed, String readFunction, String mapTargetName) {
        if(mapTargetName != null) {
            if(repeated) {
                return "%s.add(%s)".formatted(name, readFunction);
            }else {
                return "%s.put(index, %s)".formatted(mapTargetName, readFunction);
            }
        }

        if (!repeated) {
            return "%s = %s".formatted(name, readFunction);
        }

        var repeatedMethod = packed ? "addAll" : "add";
        return "%s.%s(%s)".formatted(name, repeatedMethod, readFunction);
    }

    private String getConvertedValue(int index, String streamName, ProtobufPropertyType implementation, boolean rawGroup, String readMethod) {
        var result = readMethod.isEmpty() ? streamName : "%s.%s()".formatted(streamName, readMethod);
        if((implementation.protobufType() == ProtobufType.OBJECT || implementation.protobufType() == ProtobufType.GROUP) && implementation instanceof ProtobufPropertyType.NormalType normalType) {
            var elementType = (DeclaredType) implementation.deserializedParameterType();
            var elementSpecName = getSpecFromObject(elementType);
            if(elementType.asElement().getKind() == ElementKind.ENUM) {
                var defaultValue = normalType.deserializedDefaultValue()
                        .orElseGet(normalType::defaultValue);
                result = "%s.decode(%s).orElse(%s)".formatted(elementSpecName, result, defaultValue);
            } else if(implementation.protobufType() == ProtobufType.GROUP) {
                if(!rawGroup) {
                    result = "%s.decode(%s, %s)".formatted(elementSpecName, index, result);
                }
            }else {
                result = "%s.decode(%s.lengthDelimitedStream())".formatted(elementSpecName, result);
            }
        }

        for (var i = implementation.deserializers().size() - 1; i >= 0; i--) {
            var converter = implementation.deserializers().get(i);
            var converterWrapperClass = (TypeElement) converter.delegate().getEnclosingElement();
            var converterMethodName = converter.delegate().getSimpleName();
            result = "%s.%s(%s)".formatted(converterWrapperClass.getQualifiedName(), converterMethodName, result);
        }

        return result;
    }

    private boolean isRawGroup(ProtobufPropertyType type) {
        return type.protobufType() == ProtobufType.GROUP
                && !type.deserializers().isEmpty()
                && type.deserializers().getLast().parameterType() instanceof DeclaredType parameterType
                && parameterType.asElement().getAnnotation(ProtobufGroup.class) == null;
    }

    // Returns the method to use to deserialize a property from ProtobufInputStream
    private String getDeserializerStreamMethod(ProtobufPropertyType type, boolean packed) {
        if (isEnum(type)) {
            return packed ? "readInt32Packed" : "readInt32";
        }
        
        return switch (type.protobufType()) {
            case STRING -> "readString";
            case UNKNOWN -> throw new IllegalArgumentException("Internal bug: unknown types should not reach getDeserializerStreamMethod");
            case OBJECT, GROUP -> "";
            case BYTES -> "readBytes";
            case BOOL -> packed ? "readBoolPacked" : "readBool";
            case INT32, SINT32, UINT32 -> packed ? "readInt32Packed" : "readInt32";
            case MAP -> throw new IllegalArgumentException("Internal bug: map types should not reach getDeserializerStreamMethod");
            case FLOAT -> packed ? "readFloatPacked" : "readFloat";
            case DOUBLE -> packed ? "readDoublePacked" : "readDouble";
            case FIXED32, SFIXED32 -> packed ? "readFixed32Packed" : "readFixed32";
            case INT64, SINT64, UINT64 -> packed ? "readInt64Packed" : "readInt64";
            case FIXED64, SFIXED64 -> packed ? "readFixed64Packed" : "readFixed64";
        };
    }

    protected boolean isEnum(ProtobufPropertyType type) {
        return type instanceof ProtobufPropertyType.NormalType normalType
                && normalType.serializedType() instanceof DeclaredType declaredType
                && declaredType.asElement().getKind() == ElementKind.ENUM;
    }
}

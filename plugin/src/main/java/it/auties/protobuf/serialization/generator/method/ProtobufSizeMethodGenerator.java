package it.auties.protobuf.serialization.generator.method;

import it.auties.protobuf.annotation.ProtobufGroup;
import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.model.ProtobufWireType;
import it.auties.protobuf.serialization.model.converter.ProtobufSerializerElement;
import it.auties.protobuf.serialization.model.object.ProtobufObjectElement;
import it.auties.protobuf.serialization.model.property.ProtobufGroupPropertyElement;
import it.auties.protobuf.serialization.model.property.ProtobufPropertyElement;
import it.auties.protobuf.serialization.model.property.ProtobufPropertyType;
import it.auties.protobuf.serialization.model.property.ProtobufPropertyType.NormalType;
import it.auties.protobuf.serialization.support.JavaWriter.BodyWriter;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter.MethodWriter;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.*;

public class ProtobufSizeMethodGenerator extends ProtobufMethodGenerator {
    public static final String METHOD_NAME = "sizeOf";
    private static final String INPUT_OBJECT_PARAMETER = "protoInputObject";
    private static final String OUTPUT_SIZE_NAME = "protoOutputSize";
    private static final String GROUP_INDEX_PARAMETER = "protoGroupIndex";

    private final Set<String> deferredGroupSizers;
    public ProtobufSizeMethodGenerator(ProtobufObjectElement element) {
        super(element);
        this.deferredGroupSizers = new HashSet<>();
    }

    static String getMapPropertyMethodName(String name) {
        return METHOD_NAME + name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    @Override
    protected void doInstrumentation(ClassWriter classWriter, MethodWriter methodWriter) {
        try(var ifWriter = methodWriter.printIfStatement("%s == null".formatted(INPUT_OBJECT_PARAMETER))) {
            ifWriter.printReturn("0");
        }

        if(message.isEnum()) {
            writeEnumCalculator(methodWriter);
        }else {
            writeMessageCalculator(classWriter, methodWriter);
        }
    }

    private void writeEnumCalculator(MethodWriter writer) {
        var metadata = message.enumMetadata()
                .orElseThrow(() -> new NoSuchElementException("Missing metadata from enum"));
        if(metadata.isJavaEnum()) {
            writer.printReturn("ProtobufOutputStream.getVarIntSize(%s.ordinal())".formatted(INPUT_OBJECT_PARAMETER));
        }else {
            var fieldName = metadata.field()
                    .getSimpleName();
            writer.printReturn("ProtobufOutputStream.getVarIntSize(%s.%s)".formatted(INPUT_OBJECT_PARAMETER, fieldName));
        }
    }

    private void writeMessageCalculator(ClassWriter classWriter, MethodWriter methodWriter) {
        methodWriter.printVariableDeclaration(OUTPUT_SIZE_NAME,"0");
        if(message.isGroup()) {
            methodWriter.println("%s += ProtobufOutputStream.getFieldSize(%s, %s);".formatted(OUTPUT_SIZE_NAME, GROUP_INDEX_PARAMETER, "ProtobufWireType.WIRE_TYPE_START_OBJECT"));
            methodWriter.println("%s += ProtobufOutputStream.getFieldSize(%s, %s);".formatted(OUTPUT_SIZE_NAME, GROUP_INDEX_PARAMETER, "ProtobufWireType.WIRE_TYPE_END_OBJECT"));
        }

        for(var property : message.properties()) {
            switch (property.type()) {
                case ProtobufPropertyType.CollectionType collectionType -> writeRepeatedPropertySize(classWriter, methodWriter, property.index(), property.name(), getAccessorCall(property.accessor()), property.packed(), collectionType, true, false);
                case ProtobufPropertyType.MapType mapType -> writeMapPropertySize(classWriter, methodWriter, property.index(), property.name(), getAccessorCall(property.accessor()), mapType, true, false);
                case NormalType normalType -> writeNormalPropertySize(classWriter, methodWriter, property, normalType);
            }
        }
        methodWriter.printReturn(OUTPUT_SIZE_NAME);
    }

    private void writeRepeatedPropertySize(ClassWriter classWriter, BodyWriter writer, int index, String name, String accessor, boolean packed, ProtobufPropertyType.CollectionType collectionType, boolean nullCheck, boolean cast) {
        if(packed) {
            var methodName = switch (collectionType.value().protobufType()) {
                case FLOAT, FIXED32, SFIXED32 -> "getFixed32PackedSize";
                case DOUBLE, FIXED64, SFIXED64 -> "getFixed64PackedSize";
                case BOOL -> "getFixedBoolPackedSize";
                case INT32, SINT32, UINT32, INT64, SINT64, UINT64 -> "getVarIntPackedSize";
                default -> throw new IllegalArgumentException("Internal bug: unexpected packed type " + collectionType.value().protobufType());
            };
            writer.println("%s += ProtobufOutputStream.%s(%s, %s);".formatted(OUTPUT_SIZE_NAME, methodName, index, accessor));
        }else {
            var bodyWriter = nullCheck ? writer.printIfStatement(accessor + " != null") : writer;
            var repeatedEntryFieldName = name + "Entry";
            try(var forEachWriter = bodyWriter.printForEachStatement(repeatedEntryFieldName, accessor)) {
                try(var valueIfWriter = forEachWriter.printIfStatement("%s != null".formatted(repeatedEntryFieldName))) {
                    writeAccessiblePropertySize(classWriter, valueIfWriter, index, name, collectionType, repeatedEntryFieldName, repeatedEntryFieldName, cast);
                }
            }
            if(nullCheck) {
                bodyWriter.close();
            }
        }
    }

    private void writeMapPropertySize(ClassWriter classWriter, BodyWriter methodWriter, int index, String name, String accessor, ProtobufPropertyType.MapType mapType, boolean nullCheck, boolean cast) {
        var mapFieldName = name + "MapField";
        methodWriter.printVariableDeclaration(mapFieldName, accessor);
        var methodName = getMapPropertyMethodName(name);
        deferredOperations.add(() -> writeMapEntryPropertySizeMethod(classWriter, name, mapType, methodName, cast));
        var writer = nullCheck ? methodWriter.printIfStatement(mapFieldName + " != null") : methodWriter;
        var mapEntryFieldName = name + "MapEntry";
        try(var forEachWriter = writer.printForEachStatement(mapEntryFieldName, mapFieldName + ".entrySet()")) {
            writeFieldTagSize(forEachWriter, index, ProtobufType.MAP);
            var mapEntrySizeFieldName = mapEntryFieldName + "Size";
            forEachWriter.printVariableDeclaration(mapEntrySizeFieldName, "%s(%s%s)".formatted(methodName, cast ? "(java.util.Map.Entry) " : "", mapEntryFieldName));
            forEachWriter.println("%s += ProtobufOutputStream.getVarIntSize(%s);".formatted(OUTPUT_SIZE_NAME, mapEntrySizeFieldName));
            forEachWriter.println("%s += %s;".formatted(OUTPUT_SIZE_NAME, mapEntrySizeFieldName));
        }
        if(nullCheck) {
            writer.close();
        }
    }

    private void writeMapEntryPropertySizeMethod(ClassWriter classWriter, String name, ProtobufPropertyType.MapType mapType, String methodName, boolean cast) {
        var parameter = "java.util.Map.Entry<%s, %s> %s".formatted(getQualifiedName(mapType.keyType().accessorType()), getQualifiedName( mapType.valueType().accessorType()), INPUT_OBJECT_PARAMETER);
        try (var methodWriter = classWriter.printMethodDeclaration(List.of("private", "static"), "int", methodName, parameter)) {
            methodWriter.printVariableDeclaration(OUTPUT_SIZE_NAME, "0");
            var mapKeyFieldName = INPUT_OBJECT_PARAMETER + "MapKey";
            methodWriter.printVariableDeclaration(mapKeyFieldName, INPUT_OBJECT_PARAMETER + ".getKey()");
            writeAccessiblePropertySize(classWriter, methodWriter, 1, name, mapType.keyType(), mapKeyFieldName, mapKeyFieldName, cast);
            var mapValueFieldName = name + "MapValue";
            methodWriter.printVariableDeclaration(mapValueFieldName, INPUT_OBJECT_PARAMETER + ".getValue()");
            try(var valueIfWriter = methodWriter.printIfStatement("%s != null".formatted(mapValueFieldName))) {
                writeAccessiblePropertySize(classWriter, valueIfWriter, 2, name, mapType.valueType(), mapValueFieldName, mapValueFieldName, cast);
            }
            methodWriter.printReturn(OUTPUT_SIZE_NAME);
        }
    }

    private void writeNormalPropertySize(ClassWriter classWriter, MethodWriter writer, ProtobufPropertyElement property, NormalType normalType) {
        writer.printVariableDeclaration(property.name(), getAccessorCall(property));
        if(property.required() || property.type().accessorType().getKind().isPrimitive()) {
            writeAccessiblePropertySize(classWriter, writer, property.index(), property.name(), normalType, property.name(), property.name(), false);
            return;
        }

        try(var ifWriter = writer.printIfStatement(property.name()  + " != null")) {
            writeAccessiblePropertySize(classWriter, ifWriter, property.index(), property.name(), normalType, property.name(), property.name(), false);
        }
    }

    private void writeAccessiblePropertySize(ClassWriter classWriter, BodyWriter writer, int index, String name, ProtobufPropertyType type, String variableName, String variableContent, boolean cast) {
        var innerType = type instanceof ProtobufPropertyType.CollectionType collectionType ? collectionType.value() : type;
        if(innerType.serializers().isEmpty()) {
            writeAccessiblePropertySizeDirect(classWriter, writer, index, name, innerType.protobufType(), innerType.accessorType(), cast ? type.accessorType() : null, variableContent, innerType.serializers());
            return;
        }

        var result = getPropertyVariables(variableName, variableContent, innerType);
        var nestedWriters = new LinkedList<BodyWriter>();
        nestedWriters.add(writer);
        for(var i = 1; i < result.variables().size(); i++) {
            var variable = result.variables().get(i);
            nestedWriters.getLast().printVariableDeclaration(variable.name(), variable.value());
            if(!variable.primitive()) {
                var newWriter = nestedWriters.getLast().printIfStatement("%s != null".formatted(variableName + (i - 1)));
                nestedWriters.add(newWriter);
            }
        }

        var lastVariable = result.variables().getLast();
        writeAccessiblePropertySizeDirect(classWriter, nestedWriters.getLast(), index, name, innerType.protobufType(), lastVariable.type(), cast ? lastVariable.type() : null, lastVariable.name(), innerType.serializers());

        for (var i = nestedWriters.size() - 1; i >= 1; i--) {
            var nestedWriter = nestedWriters.get(i);
            nestedWriter.close();
        }
    }

    private void writeAccessiblePropertySizeDirect(ClassWriter classWriter, BodyWriter writer, int index, String name, ProtobufType protobufType, TypeMirror type, TypeMirror castType, String accessor, List<ProtobufSerializerElement> serializers) {
        writeFieldTagSize(writer, index, protobufType);
        switch (protobufType) {
            case OBJECT -> {
                var serializedObjectFieldName = accessor + "SerializedSize";
                writer.printVariableDeclaration(serializedObjectFieldName, "%s.%s(%s)".formatted(getSpecFromObject(type), name(), accessor));
                if (type instanceof DeclaredType declaredType && declaredType.asElement().getKind() != ElementKind.ENUM) {
                    writer.println("%s += ProtobufOutputStream.getVarIntSize(%s);".formatted(OUTPUT_SIZE_NAME, serializedObjectFieldName));
                }
                writer.println("%s += %s;".formatted(OUTPUT_SIZE_NAME, serializedObjectFieldName));
            }
            case GROUP -> {
                if(serializers.isEmpty() || (serializers.getLast().returnType() instanceof DeclaredType serializedType && serializedType.asElement().getAnnotation(ProtobufGroup.class) != null)) {
                    var serializedObjectFieldName = accessor + "SerializedSize";
                    writer.printVariableDeclaration(serializedObjectFieldName, "%s.%s(%s, %s%s)".formatted(getSpecFromObject(type), name(), index, castType != null ? "(%s) ".formatted(castType) : "", accessor));
                    writer.println("%s += %s;".formatted(OUTPUT_SIZE_NAME, serializedObjectFieldName));
                } else {
                    var typeName = getSimpleName(serializers.getLast().parameterType());
                    var methodName = name() + typeName;
                    if(deferredGroupSizers.add(methodName)) {
                        deferredOperations.add(() -> writeRawGroupSizeOfCalculator(classWriter, name, methodName, accessor, serializers.getLast().groupProperties()));
                    }

                    writer.println("%s += %s(%s, %s%s);".formatted(OUTPUT_SIZE_NAME, methodName, index, castType != null ? "(java.util.Map) " : "", accessor));
                }
            }
            default -> {
                var protobufSize = switch (protobufType) {
                    case BOOL -> "1";
                    case STRING -> "ProtobufOutputStream.getStringSize(%s%s)".formatted(castType != null ? "(%s) ".formatted(castType) : "", accessor);
                    case BYTES -> "ProtobufOutputStream.getBytesSize(%s%s)".formatted(castType != null ? "(%s) ".formatted(castType) : "", accessor);
                    case INT32, SINT32, UINT32, INT64, SINT64, UINT64 ->
                            "ProtobufOutputStream.getVarIntSize(%s%s)".formatted(castType != null ? "(%s) ".formatted(castType) : "", accessor);
                    case FIXED32, SFIXED32, FLOAT -> "4";
                    case FIXED64, SFIXED64, DOUBLE -> "8";
                    default ->
                            throw new IllegalArgumentException("Internal bug: %s property types should not reach writeAccessiblePropertySizeDirect".formatted(protobufType.name()));
                };
                writer.println("%s += %s;".formatted(OUTPUT_SIZE_NAME, protobufSize));
            }
        }
    }

    private void writeRawGroupSizeOfCalculator(ClassWriter classWriter, String name, String methodName, String accessor, Map<Integer, ProtobufGroupPropertyElement> groupProperties) {
        try (var methodWriter = classWriter.printMethodDeclaration(List.of("private", "static"), "int", methodName, "int groupIndex, java.util.Map<Integer, Object> " + INPUT_OBJECT_PARAMETER)) {
            methodWriter.printVariableDeclaration(OUTPUT_SIZE_NAME, "0");
            methodWriter.println("%s += ProtobufOutputStream.getFieldSize(groupIndex, %s);".formatted(OUTPUT_SIZE_NAME, "ProtobufWireType.WIRE_TYPE_START_OBJECT"));
            methodWriter.println("%s += ProtobufOutputStream.getFieldSize(groupIndex, %s);".formatted(OUTPUT_SIZE_NAME, "ProtobufWireType.WIRE_TYPE_END_OBJECT"));
            var protoGroupEntry = accessor + "ProtoGroupEntry";
            try (var forEachBody = methodWriter.printForEachStatement(protoGroupEntry, "%s.entrySet()".formatted(INPUT_OBJECT_PARAMETER))) {
                    try (var switchBody = forEachBody.printSwitchStatement(protoGroupEntry + ".getKey()")) {
                        for (var entry : groupProperties.entrySet()) {
                            try (var switchCaseBody = switchBody.printSwitchBranch(String.valueOf(entry.getKey()))) {
                                var cast = entry.getValue().type().protobufType() == ProtobufType.GROUP || entry.getValue().type().protobufType() == ProtobufType.OBJECT;
                                var propertyValueName = switchCaseBody.printVariableDeclaration(accessor + "ProtoGroupEntryValue", "%s%s.getValue()".formatted(cast ? "(%s) ".formatted(entry.getValue().type().descriptorElementType()) : "", protoGroupEntry));
                                try (var nullCheck = switchCaseBody.printIfStatement(propertyValueName + " != null")) {
                                    switch (entry.getValue().type()) {
                                        case ProtobufPropertyType.CollectionType collectionType -> {
                                            var collectionField = "((java.util.Collection) %s)".formatted(propertyValueName);
                                            writeRepeatedPropertySize(classWriter, nullCheck, entry.getKey(), propertyValueName, collectionField, entry.getValue().packed(), collectionType, false, true);
                                        }
                                        case ProtobufPropertyType.MapType mapType -> {
                                            var mapField = "((java.util.Map) %s)".formatted(propertyValueName);
                                            writeMapPropertySize(classWriter, nullCheck, entry.getKey(), name + entry.getKey(), mapField, mapType, false, true);
                                        }
                                        case NormalType normalType -> writeAccessiblePropertySize(classWriter, nullCheck, entry.getKey(), name, normalType, propertyValueName, propertyValueName, true);
                                    }
                                }
                            }
                        }
                }
            }
            methodWriter.printReturn(OUTPUT_SIZE_NAME);
        }
    }

    private void writeFieldTagSize(BodyWriter writer, int index, ProtobufType protobufType) {
        if(protobufType == ProtobufType.GROUP) {
            return;
        }

        var wireType = switch (protobufType) {
            case GROUP -> throw new IllegalArgumentException("Internal bug: group property types should not reach writeFieldTagSize");
            case OBJECT, STRING, BYTES, MAP -> ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED;
            case FLOAT, FIXED32, SFIXED32 -> ProtobufWireType.WIRE_TYPE_FIXED32;
            case DOUBLE, SFIXED64, FIXED64 -> ProtobufWireType.WIRE_TYPE_FIXED64;
            case BOOL, INT32, SINT32, UINT32, INT64, UINT64, SINT64 -> ProtobufWireType.WIRE_TYPE_VAR_INT;
            case UNKNOWN -> throw new IllegalArgumentException("Internal bug: unknown property types should not reach writeFieldTagSize");
        };
        writer.println("%s += ProtobufOutputStream.getFieldSize(%s, %s);".formatted(OUTPUT_SIZE_NAME, index, wireType));
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
    protected String returnType() {
        return "int";
    }

    @Override
    public String name() {
        return METHOD_NAME;
    }

    @Override
    protected List<String> parametersTypes() {
        var objectType = message.element().getSimpleName().toString();
        if(message.isGroup()) {
            return List.of("int", objectType);
        }else {
            return List.of(objectType);
        }
    }

    @Override
    protected List<String> parametersNames() {
        if(message.isGroup()) {
            return List.of(GROUP_INDEX_PARAMETER, INPUT_OBJECT_PARAMETER);
        }else {
            return List.of(INPUT_OBJECT_PARAMETER);
        }
    }

    private String getAccessorCall(ProtobufPropertyElement property) {
        return getAccessorCall(property.accessor());
    }

    private String getAccessorCall(Element accessor) {
        return getAccessorCall(INPUT_OBJECT_PARAMETER, accessor);
    }
}

package it.auties.protobuf.serialization.generator.method;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.model.ProtobufWireType;
import it.auties.protobuf.serialization.object.ProtobufObjectElement;
import it.auties.protobuf.serialization.property.ProtobufPropertyElement;
import it.auties.protobuf.serialization.property.ProtobufPropertyType;
import it.auties.protobuf.serialization.property.ProtobufPropertyType.NormalType;
import it.auties.protobuf.serialization.support.JavaWriter.BodyWriter;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter.MethodWriter;

import javax.lang.model.type.TypeMirror;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

public class ProtobufSizeMethodGenerator extends ProtobufMethodGenerator {
    public static final String METHOD_NAME = "sizeOf";
    private static final String DEFAULT_PARAMETER_NAME = "object";
    private static final String DEFAULT_RESULT_NAME = "protoSize";

    public ProtobufSizeMethodGenerator(ProtobufObjectElement element) {
        super(element);
    }

    static String getMapPropertyMethodName(ProtobufPropertyElement property) {
        return METHOD_NAME + property.name().substring(0, 1).toUpperCase() + property.name().substring(1);
    }

    @Override
    protected void doInstrumentation(ClassWriter classWriter, MethodWriter methodWriter) {
        try(var ifWriter = methodWriter.printIfStatement("%s == null".formatted(DEFAULT_PARAMETER_NAME))) {
            ifWriter.printReturn("0");
        }

        if(message.isEnum()) {
            writeEnumCalculator(methodWriter);
        }else {
            writeMessageCalculator(classWriter, methodWriter);
        }
    }

    private void writeEnumCalculator(MethodWriter writer) {
        var fieldName = message.enumMetadata()
                .orElseThrow(() -> new NoSuchElementException("Missing metadata from enum"))
                .field()
                .getSimpleName();
        writer.printReturn("ProtobufOutputStream.getVarIntSize(%s.%s)".formatted(DEFAULT_PARAMETER_NAME, fieldName));
    }

    private void writeMessageCalculator(ClassWriter classWriter, MethodWriter methodWriter) {
        methodWriter.printVariableDeclaration(DEFAULT_RESULT_NAME, "0");
        for(var property : message.properties()) {
            switch (property.type()) {
                case ProtobufPropertyType.CollectionType collectionType -> writeRepeatedPropertySize(methodWriter, property, collectionType);
                case ProtobufPropertyType.MapType mapType -> writeMapPropertySize(classWriter, methodWriter, property, mapType);
                case NormalType normalType -> writeNormalPropertySize(methodWriter, property, normalType);
            }
        }
        methodWriter.printReturn(DEFAULT_RESULT_NAME);
    }

    private void writeRepeatedPropertySize(MethodWriter writer, ProtobufPropertyElement property, ProtobufPropertyType.CollectionType collectionType) {
        var repeatedFieldName = property.name() + "RepeatedField";
        writer.printVariableDeclaration(repeatedFieldName, getAccessorCall(property));
        try(var ifWriter = writer.printIfStatement(repeatedFieldName + " != null")) {
            var repeatedEntryFieldName = property.name() + "Entry";
            try(var forEachWriter = ifWriter.printForEachStatement(repeatedEntryFieldName, repeatedFieldName)) {
                try(var valueIfWriter = forEachWriter.printIfStatement("%s != null".formatted(repeatedEntryFieldName))) {
                    writeAccessiblePropertySize(valueIfWriter, property.index(), collectionType.value(), repeatedEntryFieldName, repeatedEntryFieldName);
                }
            }
        }
    }

    private void writeMapPropertySize(ClassWriter classWriter, MethodWriter methodWriter, ProtobufPropertyElement property, ProtobufPropertyType.MapType mapType) {
        var mapFieldName = property.name() + "MapField";
        methodWriter.printVariableDeclaration(mapFieldName, getAccessorCall(property));
        var methodName = getMapPropertyMethodName(property);
        deferredOperations.add(() -> writeMapEntryPropertySizeMethod(classWriter, property, mapType, methodName));
        try(var ifWriter = methodWriter.printIfStatement(mapFieldName + " != null")) {
            var mapEntryFieldName = property.name() + "MapEntry";
            try(var forEachWriter = ifWriter.printForEachStatement(mapEntryFieldName, mapFieldName + ".entrySet()")) {
                writeFieldTagSize(forEachWriter, property.index(), ProtobufType.MAP);
                var mapEntrySizeFieldName = mapEntryFieldName + "Size";
                forEachWriter.printVariableDeclaration(mapEntrySizeFieldName, "%s(%s)".formatted(methodName, mapEntryFieldName));
                forEachWriter.println("%s += ProtobufOutputStream.getVarIntSizeUnsigned(%s);".formatted(DEFAULT_RESULT_NAME, mapEntrySizeFieldName));
                forEachWriter.println("%s += %s;".formatted(DEFAULT_RESULT_NAME, mapEntrySizeFieldName));
            }
        }
    }

    private void writeMapEntryPropertySizeMethod(ClassWriter classWriter, ProtobufPropertyElement property, ProtobufPropertyType.MapType mapType, String methodName) {
        var parameter = "java.util.Map.Entry<%s, %s> %s".formatted(mapType.keyType().accessorType(), mapType.valueType().accessorType(), DEFAULT_PARAMETER_NAME);
        try (var methodWriter = classWriter.printMethodDeclaration(List.of("private", "static"), "int", methodName, parameter)) {
            methodWriter.printVariableDeclaration(DEFAULT_RESULT_NAME, "0");
            var mapKeyFieldName = DEFAULT_PARAMETER_NAME + "MapKey";
            methodWriter.printVariableDeclaration(mapKeyFieldName, DEFAULT_PARAMETER_NAME + ".getValue()");
            writeAccessiblePropertySize(methodWriter, 1, mapType.keyType(), mapKeyFieldName, DEFAULT_PARAMETER_NAME + ".getKey()");
            var mapValueFieldName = property.name() + "MapValue";
            methodWriter.printVariableDeclaration(mapValueFieldName, DEFAULT_PARAMETER_NAME + ".getValue()");
            try(var valueIfWriter = methodWriter.printIfStatement("%s != null".formatted(mapValueFieldName))) {
                writeAccessiblePropertySize(valueIfWriter, 2, mapType.valueType(), mapValueFieldName, mapValueFieldName);
            }
            methodWriter.printReturn(DEFAULT_RESULT_NAME);
        }
    }

    private void writeNormalPropertySize(MethodWriter writer, ProtobufPropertyElement property, NormalType normalType) {
        writer.printVariableDeclaration(property.name(), getAccessorCall(property));
        if(property.required() || property.type().accessorType().getKind().isPrimitive()) {
            writeAccessiblePropertySize(writer, property.index(), normalType, property.name(), property.name());
            return;
        }

        try(var ifWriter = writer.printIfStatement(property.name()  + " != null")) {
            writeAccessiblePropertySize(ifWriter, property.index(), normalType, property.name(), property.name());
        }
    }

    private void writeAccessiblePropertySize(BodyWriter writer, int index, NormalType type, String variableName, String variableContent) {
        if(type.serializers().isEmpty()) {
            writeAccessiblePropertySizeDirect(writer, index, type.protobufType(), type.accessorType(), variableContent);
            return;
        }

        var result = getVariables(variableName, variableContent, type);
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
        writeAccessiblePropertySizeDirect(nestedWriters.getLast(), index, type.protobufType(), lastVariable.type(), lastVariable.name());

        for (var i = nestedWriters.size() - 1; i >= 1; i--) {
            var nestedWriter = nestedWriters.get(i);
            nestedWriter.close();
        }
    }

    private void writeAccessiblePropertySizeDirect(BodyWriter writer, int index, ProtobufType protobufType, TypeMirror javaType, String accessor) {
        writeFieldTagSize(writer, index, protobufType);
        if(protobufType == ProtobufType.OBJECT) {
            var serializedObjectFieldName = accessor + "SerializedSize";
            writer.printVariableDeclaration(serializedObjectFieldName, "%s.%s(%s)".formatted(getSpecFromObject(javaType), name(), accessor));
            writer.println("%s += ProtobufOutputStream.getVarIntSize(%s);".formatted(DEFAULT_RESULT_NAME, serializedObjectFieldName));
            writer.println("%s += %s;".formatted(DEFAULT_RESULT_NAME, serializedObjectFieldName));
            return;
        }

        var protobufSize = switch (protobufType) {
            case BOOL -> "1";
            case STRING -> "ProtobufOutputStream.getStringSize(%s)".formatted(accessor);
            case BYTES -> "ProtobufOutputStream.getBytesSize(%s)".formatted(accessor);
            case INT32, SINT32, UINT32, INT64, SINT64, UINT64 -> "ProtobufOutputStream.getVarIntSize(%s)".formatted(accessor);
            case FIXED32, SFIXED32, FLOAT -> "4";
            case FIXED64, SFIXED64, DOUBLE -> "8";
            default -> throw new IllegalStateException("Unexpected value: " + protobufType);
        };
        writer.println("%s += %s;".formatted(DEFAULT_RESULT_NAME, protobufSize));
    }

    private void writeFieldTagSize(BodyWriter writer, int index, ProtobufType protobufType) {
        var wireType = switch (protobufType) {
            case OBJECT, STRING, BYTES, MAP -> ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED;
            case FLOAT, FIXED32, SFIXED32 -> ProtobufWireType.WIRE_TYPE_FIXED32;
            case DOUBLE, SFIXED64, FIXED64 -> ProtobufWireType.WIRE_TYPE_FIXED64;
            case BOOL, INT32, SINT32, UINT32, INT64, UINT64, SINT64 -> ProtobufWireType.WIRE_TYPE_VAR_INT;
        };
        writer.println("%s += ProtobufOutputStream.getFieldSize(%s, %s);".formatted(DEFAULT_RESULT_NAME, index, wireType));
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
        return List.of(message.element().getSimpleName().toString());
    }

    @Override
    protected List<String> parametersNames() {
        return List.of(DEFAULT_PARAMETER_NAME);
    }

    private String getAccessorCall(ProtobufPropertyElement property) {
        return getAccessorCall(DEFAULT_PARAMETER_NAME, property);
    }
}

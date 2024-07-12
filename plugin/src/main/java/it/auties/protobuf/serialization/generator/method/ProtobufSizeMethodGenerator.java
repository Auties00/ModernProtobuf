package it.auties.protobuf.serialization.generator.method;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.model.ProtobufWireType;
import it.auties.protobuf.serialization.object.ProtobufObjectElement;
import it.auties.protobuf.serialization.property.ProtobufPropertyElement;
import it.auties.protobuf.serialization.property.ProtobufPropertyType;
import it.auties.protobuf.serialization.property.ProtobufPropertyType.NormalType;
import it.auties.protobuf.serialization.support.JavaWriter.BodyWriter;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter.MethodWriter;

import javax.lang.model.type.TypeMirror;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

public class ProtobufSizeMethodGenerator extends ProtobufMethodGenerator {
    public static final String METHOD_NAME = "sizeOf";
    private static final String DEFAULT_PARAMETER_NAME = "object";
    private static final String DEFAULT_RESULT_NAME = "size";

    public ProtobufSizeMethodGenerator(ProtobufObjectElement element) {
        super(element);
    }

    @Override
    protected void doInstrumentation(MethodWriter writer) {
        try(var ifWriter = writer.printIfStatement("%s == null".formatted(DEFAULT_PARAMETER_NAME))) {
            ifWriter.printReturn("0");
        }

        if(message.isEnum()) {
            writeEnumCalculator(writer);
        }else {
            writeMessageCalculator(writer);
        }
    }

    private void writeEnumCalculator(MethodWriter writer) {
        var fieldName = message.enumMetadata()
                .orElseThrow(() -> new NoSuchElementException("Missing metadata from enum"))
                .field()
                .getSimpleName();
        writer.printReturn("ProtobufOutputStream.getVarIntSize(%s.%s)".formatted(DEFAULT_PARAMETER_NAME, fieldName));
    }

    private void writeMessageCalculator(MethodWriter writer) {
        writer.printVariableDeclaration(DEFAULT_RESULT_NAME, "0");
        for(var property : message.properties()) {
            switch (property.type()) {
                case ProtobufPropertyType.CollectionType collectionType -> writeRepeatedPropertySerializer1(writer, property, collectionType);
                case ProtobufPropertyType.MapType mapType -> writeMapPropertySerializer(writer, property, mapType);
                case NormalType normalType -> writeNormalPropertySize(writer, property, normalType);
            }
        }
        writer.printReturn(DEFAULT_RESULT_NAME);
    }

    private void writeRepeatedPropertySerializer1(MethodWriter writer, ProtobufPropertyElement property, ProtobufPropertyType.CollectionType collectionType) {
        var repeatedFieldName = property.name() + "RepeatedField";
        writer.printVariableDeclaration(repeatedFieldName, getAccessorCall(property));
        try(var ifWriter = writer.printIfStatement(repeatedFieldName + " != null")) {
            var repeatedEntryFieldName = property.name() + "Entry";
            try(var forEachWriter = ifWriter.printForEachStatement(repeatedEntryFieldName, repeatedFieldName)) {
                try(var valueIfWriter = forEachWriter.printIfStatement("%s != null".formatted(repeatedEntryFieldName))) {
                    writeAccessiblePropertySize(valueIfWriter, property.index(), collectionType.value(), repeatedEntryFieldName);
                }
            }
        }
    }

    private void writeMapPropertySerializer(MethodWriter writer, ProtobufPropertyElement property, ProtobufPropertyType.MapType mapType) {
        var mapFieldName = property.name() + "MapField";
        writer.printVariableDeclaration(mapFieldName, getAccessorCall(property));
        try(var ifWriter = writer.printIfStatement(mapFieldName + " != null")) {
            var mapEntryFieldName = property.name() + "MapEntry";
            try(var forEachWriter = ifWriter.printForEachStatement(mapEntryFieldName, mapFieldName + ".entrySet()")) {
                writeAccessiblePropertySize(forEachWriter, property.index(), mapType.keyType(), mapEntryFieldName + ".getKey()");
                var mapKeyValue = property.name() + "MapValue";
                forEachWriter.printVariableDeclaration(mapKeyValue, mapEntryFieldName + ".getValue()");
                try(var valueIfWriter = forEachWriter.printIfStatement("%s != null".formatted(mapKeyValue))) {
                    writeAccessiblePropertySize(valueIfWriter, property.index(), mapType.valueType(), mapKeyValue);
                }
            }
        }
    }

    private void writeNormalPropertySize(MethodWriter writer, ProtobufPropertyElement property, NormalType normalType) {
        writer.printVariableDeclaration(property.name(), getAccessorCall(property));
        if(property.required() || property.type().isPrimitive()) {
            writeAccessiblePropertySize(writer, property.index(), normalType, property.name());
            return;
        }

        try(var ifWriter = writer.printIfStatement(property.name()  + " != null")) {
            writeAccessiblePropertySize(ifWriter, property.index(), normalType, property.name());
        }
    }

    private void writeAccessiblePropertySize(BodyWriter writer, int index, NormalType type, String variableName) {
        if(type.serializers().isEmpty()) {
            writeAccessiblePropertySizeDirect(writer, index, type.protobufType(), type.implementationType(), variableName);
            return;
        }

        var result = getVariables(variableName, variableName, type);
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
        var wireType = switch (protobufType) {
            case OBJECT, STRING, BYTES -> ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED;
            case FLOAT, FIXED32, SFIXED32 -> ProtobufWireType.WIRE_TYPE_FIXED32;
            case DOUBLE, SFIXED64, FIXED64 -> ProtobufWireType.WIRE_TYPE_FIXED64;
            case BOOL, INT32, SINT32, UINT32, INT64, UINT64, SINT64 -> ProtobufWireType.WIRE_TYPE_VAR_INT;
            default -> throw new IllegalStateException("Unexpected value: " + protobufType);
        };
        writer.println("%s += ProtobufOutputStream.getFieldSize(%s, %s);".formatted(DEFAULT_RESULT_NAME, index, wireType));
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

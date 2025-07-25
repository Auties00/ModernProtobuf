package it.auties.protobuf.serialization.generator.method;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.model.ProtobufWireType;
import it.auties.protobuf.serialization.model.ProtobufObjectElement;
import it.auties.protobuf.serialization.model.ProtobufPropertyElement;
import it.auties.protobuf.serialization.model.ProtobufPropertyType;
import it.auties.protobuf.serialization.writer.BodyWriter;
import it.auties.protobuf.serialization.writer.ClassWriter;

import javax.lang.model.type.TypeMirror;
import java.util.List;

public abstract class ProtobufSizeGenerator extends ProtobufSerializationGenerator {
    public static final String METHOD_NAME = "sizeOf";
    private static final String INPUT_OBJECT_PARAMETER = "protoInputObject";
    private static final String OUTPUT_SIZE_NAME = "protoOutputSize";

    public ProtobufSizeGenerator(ProtobufObjectElement element) {
        super(element);
    }

    public static String getMapPropertyMethodName(String name) {
        return METHOD_NAME + name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    protected void writeRepeatedSize(BodyWriter writer, int index, String name, String accessor, boolean packed, ProtobufPropertyType.CollectionType collectionType, boolean cast) {
        if(packed) {
            var methodName = getPackedSizeCalculator(collectionType);
            writer.println("%s += ProtobufOutputStream.%s(%s, %s);".formatted(OUTPUT_SIZE_NAME, methodName, index, accessor));
            return;
        }

        try(var bodyWriter = writer.printIfStatement(accessor + " != null")) {
            var repeatedEntryFieldName = name + "Entry";
            try(var forEachWriter = bodyWriter.printForEachStatement(repeatedEntryFieldName, accessor)) {
                writeNormalSize(
                        forEachWriter,
                        index,
                        repeatedEntryFieldName,
                        collectionType.valueType(),
                        cast ? collectionType.valueType().serializedType() : null,
                        null
                );
            }
        }
    }

    private String getPackedSizeCalculator(ProtobufPropertyType.CollectionType collectionType) {
        return switch (collectionType.valueType().protobufType()) {
            case FLOAT, FIXED32, SFIXED32 -> "getFixed32PackedSize";
            case DOUBLE, FIXED64, SFIXED64 -> "getFixed64PackedSize";
            case BOOL -> "getBoolPackedSize";
            case INT32, SINT32, UINT32, INT64, SINT64, UINT64 -> "getVarIntPackedSize";
            default ->
                    throw new IllegalArgumentException("Internal bug: unexpected packed type " + collectionType.valueType().protobufType());
        };
    }

    protected void writeMapSize(ClassWriter classWriter, BodyWriter methodWriter, int index, String name, String accessor, ProtobufPropertyType.MapType mapType, boolean cast) {
        var mapFieldName = name + "MapField";
        methodWriter.printVariableDeclaration(mapFieldName, accessor);
        var methodName = getMapPropertyMethodName(name);
        deferredOperations.add(() -> writeMapEntryPropertySizeMethod(classWriter, name, mapType, methodName, cast));
        try(var writer = methodWriter.printIfStatement(mapFieldName + " != null")) {
            var mapEntryFieldName = name + "MapEntry";
            try (var forEachWriter = writer.printForEachStatement(mapEntryFieldName, mapFieldName + ".entrySet()")) {
                writeFieldTagSize(forEachWriter, index, ProtobufType.MAP);
                var mapEntrySizeFieldName = mapEntryFieldName + "Size";
                forEachWriter.printVariableDeclaration(mapEntrySizeFieldName, "%s(%s%s)".formatted(methodName, cast ? "(java.util.Map.Entry) " : "", mapEntryFieldName));
                forEachWriter.println("%s += ProtobufOutputStream.getVarIntSize(%s);".formatted(OUTPUT_SIZE_NAME, mapEntrySizeFieldName));
                forEachWriter.println("%s += %s;".formatted(OUTPUT_SIZE_NAME, mapEntrySizeFieldName));
            }
        }
    }

    private void writeMapEntryPropertySizeMethod(ClassWriter classWriter, String name, ProtobufPropertyType.MapType mapType, String methodName, boolean cast) {
        var keyQualifiedName = getQualifiedName(mapType.keyType().accessorType());
        var valueQualifiedName = getQualifiedName(mapType.valueType().accessorType());
        var parameter = "java.util.Map.Entry<%s, %s> %s".formatted(keyQualifiedName, valueQualifiedName, INPUT_OBJECT_PARAMETER);
        try (var methodWriter = classWriter.printMethodDeclaration(List.of("private", "static"), "int", methodName, parameter)) {
            methodWriter.printVariableDeclaration(OUTPUT_SIZE_NAME, "0");
            writeNormalSize(
                    methodWriter,
                    1,
                    name + "MapKey",
                    mapType.keyType(),
                    cast ? mapType.keyType().serializedType() : null,
                    INPUT_OBJECT_PARAMETER + ".getKey()"
            );
            writeNormalSize(
                    methodWriter,
                    2,
                    name + "MapValue",
                    mapType.valueType(),
                    cast ? mapType.valueType().serializedType() : null,
                    INPUT_OBJECT_PARAMETER + ".getValue()"
            );
            methodWriter.printReturn(OUTPUT_SIZE_NAME);
        }
    }

    protected void writeNormalSize(BodyWriter writer, ProtobufPropertyElement property) {
        var accessorCall = getAccessorCall(INPUT_OBJECT_PARAMETER, property.accessor());
        writeNormalSize(
                writer,
                property.index(),
                property.name(),
                property.type(),
                null,
                accessorCall
        );
    }

    protected void writeNormalSize(BodyWriter writer, int index, String name, ProtobufPropertyType normalType, TypeMirror castType, String accessorCall) {
        writeCustomSerializer(
                writer,
                index,
                name,
                accessorCall == null ? name : accessorCall,
                normalType,
                accessorCall != null,
                true,
                castType != null,
                (nestedWriter, serializedName, serializedStatements) -> writeObjectCalculator(
                        nestedWriter,
                        index,
                        name,
                        normalType,
                        castType,
                        serializedName
                ),
                (nestedWriter, serializedName, serializedStatements) -> writePrimitiveCalculator(
                        nestedWriter,
                        index,
                        normalType.protobufType(),
                        castType,
                        serializedName
                )
        );
    }

    private void writeObjectCalculator(BodyWriter writer, int index, String name, ProtobufPropertyType type, TypeMirror castType, String accessor) {
        writeFieldTagSize(writer, index, type.protobufType());
        switch (type.protobufType()) {
            case MESSAGE, ENUM -> {
                var parameterType = type.serializers().isEmpty() ? type.descriptorElementType() : type.serializers().getLast().parameterType();
                var specName = getSpecFromObject(parameterType);
                var serializedObjectFieldName = writer.printVariableDeclaration(name + "SerializedSize", "%s.%s(%s)".formatted(specName, name(), accessor));
                if (!isEnum(parameterType)) {
                    writer.println("%s += ProtobufOutputStream.getVarIntSize(%s);".formatted(OUTPUT_SIZE_NAME, serializedObjectFieldName));
                }
                writer.println("%s += %s;".formatted(OUTPUT_SIZE_NAME, serializedObjectFieldName));
            }
            case GROUP -> {
                var lastSerializer = type.rawGroupSerializer()
                        .orElse(null);
                if (lastSerializer != null) {
                    var rawGroupSpecType = getSpecFromObject(lastSerializer.parameterType());
                    writer.println("%s += %s.%s(%s, %s%s);".formatted(OUTPUT_SIZE_NAME, rawGroupSpecType, name(), index, castType != null ? "(java.util.Map) " : "", accessor));
                } else {
                    var groupType = type.serializers().isEmpty() ? type.descriptorElementType() : type.serializers().getLast().parameterType();
                    var groupSpecType = getSpecFromObject(groupType);
                    var serializedObjectFieldName = writer.printVariableDeclaration(name + "SerializedSize", "%s.%s(%s, %s%s)".formatted(groupSpecType, name(), index, castType != null ? "(%s) ".formatted(castType) : "", accessor));
                    writer.println("%s += %s;".formatted(OUTPUT_SIZE_NAME, serializedObjectFieldName));
                }
            }
            default -> throw new IllegalArgumentException("Internal bug: %s property types should not reach writeObjectCalculator".formatted(type.protobufType().name()));
        }
    }

    private void writePrimitiveCalculator(BodyWriter writer, int index, ProtobufType protobufType, TypeMirror castType, String accessor) {
        writeFieldTagSize(writer, index, protobufType);
        var protobufSize = switch (protobufType) {
            case BOOL -> "1";
            case STRING -> "ProtobufOutputStream.getStringSize(%s%s)".formatted(castType != null ? "(%s) ".formatted(castType) : "", accessor);
            case BYTES -> "ProtobufOutputStream.getBytesSize(%s%s)".formatted(castType != null ? "(%s) ".formatted(castType) : "", accessor);
            case ENUM, INT32, SINT32, UINT32, INT64, SINT64, UINT64 ->
                    "ProtobufOutputStream.getVarIntSize(%s%s)".formatted(castType != null ? "(%s) ".formatted(castType) : "", accessor);
            case FIXED32, SFIXED32, FLOAT -> "4";
            case FIXED64, SFIXED64, DOUBLE -> "8";
            default -> throw new IllegalArgumentException("Internal bug: %s property types should not reach writePrimitiveCalculator".formatted(protobufType.name()));
        };
        writer.println("%s += %s;".formatted(OUTPUT_SIZE_NAME, protobufSize));
    }

    private void writeFieldTagSize(BodyWriter writer, int index, ProtobufType protobufType) {
        if(protobufType == ProtobufType.GROUP) {
            return;
        }

        var wireType = switch (protobufType) {
            case GROUP -> throw new IllegalArgumentException("Internal bug: group property types should not reach writeFieldTagSize");
            case MESSAGE, ENUM, STRING, BYTES, MAP -> ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED;
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
}

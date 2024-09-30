package it.auties.protobuf.serialization.generator.method.deserialization;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.serialization.generator.method.ProtobufMethodGenerator;
import it.auties.protobuf.serialization.model.property.ProtobufPropertyType;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter.SwitchStatementWriter;

import javax.lang.model.element.TypeElement;
import java.util.List;

public abstract class ProtobufDeserializationGenerator<INPUT> extends ProtobufMethodGenerator<INPUT> {
    public static final String METHOD_NAME = "decode";
    private static final String INPUT_STREAM_NAME = "protoInputStream";

    public ProtobufDeserializationGenerator(INPUT element) {
        super(element);
    }

    protected void writeMapDeserializer(SwitchStatementWriter writer, int index, String name, ProtobufPropertyType.MapType mapType) {
        try(var switchBranchWriter = writer.printSwitchBranch(String.valueOf(index))) {
            var streamName = switchBranchWriter.printVariableDeclaration("%sInputStream".formatted(name), "%s.readLengthDelimited()".formatted(INPUT_STREAM_NAME));
            var keyName = switchBranchWriter.printVariableDeclaration(getQualifiedName(mapType.keyType().accessorType()), "%sKey".formatted(name), "null");
            var valueName = switchBranchWriter.printVariableDeclaration(getQualifiedName(mapType.valueType().accessorType()), "%sValue".formatted(name), "null");
            var keyReadMethod = getDeserializerStreamMethod(mapType.keyType(), false);
            var keyReadFunction = getConvertedValue(1, streamName, mapType.keyType(), keyReadMethod);
            var valueReadMethod = getDeserializerStreamMethod(mapType.valueType(), false);
            var valueReadFunction = getConvertedValue(2, streamName, mapType.valueType(), valueReadMethod);
            try(var whileWriter = switchBranchWriter.printWhileStatement(streamName + ".readTag()")) {
                try(var mapSwitchWriter = whileWriter.printSwitchStatement(streamName + ".index()")) {
                    mapSwitchWriter.printSwitchBranch("1", "%s = %s".formatted(keyName, keyReadFunction));
                    mapSwitchWriter.printSwitchBranch("2", "%s = %s".formatted(valueName, valueReadFunction));
                }
            }
            switchBranchWriter.println("%s.put(%s, %s);".formatted(name, keyName, valueName));
        }
    }

    protected void writeDeserializer(SwitchStatementWriter writer, String name, int index, ProtobufPropertyType type, boolean repeated, boolean packed, String mapTargetName) {
        var readMethod = getDeserializerStreamMethod(type, packed);
        var readFunction = getConvertedValue(index, INPUT_STREAM_NAME, type, readMethod);
        var readAssignment = getReadAssignment(name, repeated, packed, readFunction, mapTargetName);
        writer.printSwitchBranch(String.valueOf(index), readAssignment);
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

    private String getConvertedValue(int index, String value, ProtobufPropertyType implementation, String readMethod) {
        if (implementation.protobufType() == ProtobufType.MESSAGE) {
            value = "%s.readLengthDelimited()".formatted(value);
        }

        if(!readMethod.isEmpty()) {
            value = "%s.%s()".formatted(value, readMethod);
        }

        for (var i = 0; i < implementation.deserializers().size(); i++) {
            var deserializer = implementation.deserializers().get(i);
            var parent = (TypeElement) deserializer.delegate().getEnclosingElement();
            var converterMethodName = deserializer.delegate().getSimpleName();
            switch (deserializer.delegate().getParameters().size()) {
                case 1 -> value = "%s.%s(%s)".formatted(parent.getQualifiedName(), converterMethodName, value);
                case 2 -> value = "%s.%s(%s, %s)".formatted(parent.getQualifiedName(), converterMethodName, index, value);
                default -> throw new IllegalArgumentException(
                        "Unexpected number of arguments for deserializer "
                                +  deserializer.delegate().getSimpleName()
                                + " in "
                                + parent.getQualifiedName()
                );
            }
        }

        return value;
    }

    private String getDeserializerStreamMethod(ProtobufPropertyType type, boolean packed) {
        return switch (type.protobufType()) {
            case STRING -> "readString";
            case UNKNOWN -> throw new IllegalArgumentException("Internal bug: unknown types should not reach getDeserializerStreamMethod");
            case MESSAGE, GROUP -> "";
            case ENUM, INT32, SINT32, UINT32 -> packed ? "readInt32Packed" : "readInt32";
            case BYTES -> "readBytes";
            case BOOL -> packed ? "readBoolPacked" : "readBool";
            case MAP -> throw new IllegalArgumentException("Internal bug: map types should not reach getDeserializerStreamMethod");
            case FLOAT -> packed ? "readFloatPacked" : "readFloat";
            case DOUBLE -> packed ? "readDoublePacked" : "readDouble";
            case FIXED32, SFIXED32 -> packed ? "readFixed32Packed" : "readFixed32";
            case INT64, SINT64, UINT64 -> packed ? "readInt64Packed" : "readInt64";
            case FIXED64, SFIXED64 -> packed ? "readFixed64Packed" : "readFixed64";
        };
    }

    @Override
    protected List<String> modifiers() {
        return List.of("public", "static");
    }

    @Override
    protected String name() {
        return METHOD_NAME;
    }
}

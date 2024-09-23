package it.auties.protobuf.serialization.generator.method;

import it.auties.protobuf.annotation.ProtobufGroup;
import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.serialization.model.object.ProtobufObjectElement;
import it.auties.protobuf.serialization.model.property.ProtobufPropertyElement;
import it.auties.protobuf.serialization.model.property.ProtobufPropertyType;
import it.auties.protobuf.serialization.model.property.ProtobufPropertyVariables;
import it.auties.protobuf.serialization.support.JavaWriter.BodyWriter;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter.MethodWriter;
import it.auties.protobuf.stream.ProtobufOutputStream;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.type.DeclaredType;
import java.util.*;

public class ProtobufSerializationMethodGenerator extends ProtobufMethodGenerator {
    private static final String INPUT_OBJECT_PARAMETER = "protoInputObject";
    private static final String OUTPUT_OBJECT_PARAMETER = "protoOutputStream";
    private static final String GROUP_INDEX_PARAMETER = "protoGroupIndex";

    private final Set<String> deferredGroupSerializers;
    public ProtobufSerializationMethodGenerator(ProtobufObjectElement element) {
        super(element);
        this.deferredGroupSerializers = new HashSet<>();
    }

    @Override
    protected void doInstrumentation(ClassWriter classWriter, MethodWriter writer) {
        if (message.isEnum()) {
            createEnumSerializer(writer);
        } else {
            createMessageSerializer(classWriter, writer);
        }
    }

    private void createEnumSerializer(MethodWriter writer) {
        try(var ifWriter = writer.printIfStatement("%s == null".formatted(INPUT_OBJECT_PARAMETER))) {
            ifWriter.printReturn("null");
        }

        var metadata = message.enumMetadata()
                .orElseThrow(() -> new NoSuchElementException("Missing metadata from enum"));
        if(metadata.isJavaEnum()) {
            writer.printReturn("%s.ordinal()".formatted(INPUT_OBJECT_PARAMETER));
        }else {
            var fieldName = metadata.field().getSimpleName();
            writer.printReturn("%s.%s".formatted(INPUT_OBJECT_PARAMETER, fieldName));
        }
    }

    private void createMessageSerializer(ClassWriter classWriter, MethodWriter writer) {
        try(var ifWriter = writer.printIfStatement("%s == null".formatted(INPUT_OBJECT_PARAMETER))) {
            ifWriter.printReturn();
        }

        if(message.isGroup()) {
            writer.println("%s.writeGroupStart(%s);".formatted(OUTPUT_OBJECT_PARAMETER, GROUP_INDEX_PARAMETER));
        }

        createRequiredPropertiesNullCheck(writer);
        for(var property : message.properties()) {
            switch (property.type()) {
                case ProtobufPropertyType.CollectionType collectionType -> writeRepeatedPropertySerializer(classWriter, writer, property.index(), property.name(), getAccessorCall(property.accessor()), collectionType, property.packed(), true, false);
                case ProtobufPropertyType.MapType mapType -> writeMapSerializer(classWriter, writer, property.index(), property.name(), getAccessorCall(property.accessor()), mapType, true, false);
                default -> writeAnySerializer(classWriter, writer, property.index(), property.name(), getAccessorCall(property.accessor()), property.type(), true, true, false);
            }
        }

        if(message.isGroup()) {
            writer.println("%s.writeGroupEnd(%s);".formatted(OUTPUT_OBJECT_PARAMETER, GROUP_INDEX_PARAMETER));
        }
    }

    private void createRequiredPropertiesNullCheck(MethodWriter writer) {
        message.properties()
                .stream()
                .filter(ProtobufPropertyElement::required)
                .forEach(entry -> writer.println("Objects.requireNonNull(%s, \"Missing required property: %s\");".formatted(getAccessorCall(entry.accessor()), entry.name())));
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
        return message.isEnum() ? "Integer" : "void";
    }

    @Override
    public String name() {
        return "encode";
    }

    @Override
    protected List<String> parametersTypes() {
        var objectType = message.element().getSimpleName().toString();
        if (message.isEnum()) {
            return List.of(objectType);
        }else if(message.isGroup()) {
            return List.of("int", objectType, ProtobufOutputStream.class.getSimpleName());
        }else {
            return List.of(objectType, ProtobufOutputStream.class.getSimpleName());
        }
    }

    @Override
    protected List<String> parametersNames() {
        if (message.isEnum()) {
            return List.of(INPUT_OBJECT_PARAMETER);
        }else if(message.isGroup()) {
            return List.of(GROUP_INDEX_PARAMETER, INPUT_OBJECT_PARAMETER, OUTPUT_OBJECT_PARAMETER);
        }else {
            return List.of(INPUT_OBJECT_PARAMETER, OUTPUT_OBJECT_PARAMETER);
        }
    }

    private String getAccessorCall(Element accessor) {
        return getAccessorCall(INPUT_OBJECT_PARAMETER, accessor);
    }

    private void writeRepeatedPropertySerializer(ClassWriter classWriter, BodyWriter writer, int index, String name, String accessor, ProtobufPropertyType.CollectionType collectionType, boolean packed, boolean nullCheck, boolean cast) {
        if(packed) {
            var writeMethod = getStreamMethodName(collectionType.value().protobufType(), isEnum(collectionType.value()), true);
            writer.println("%s.%s(%s, %s);".formatted(OUTPUT_OBJECT_PARAMETER, writeMethod.orElseThrow(), index, accessor));
        }else {
            var bodyWriter = nullCheck ? writer.printIfStatement("%s != null".formatted(accessor)) : writer;
            var localVariableName = "%sEntry".formatted(name); // Prevent shadowing
            try(var forEachWriter = bodyWriter.printForEachStatement(localVariableName, accessor)) {
                writeAnySerializer(classWriter, forEachWriter, index, name, localVariableName, collectionType.value(), true, false, cast);
            }
            if(nullCheck) {
                bodyWriter.close();
            }
        }
    }

    private void writeMapSerializer(ClassWriter classWriter, BodyWriter writer, int index, String name, String accessor, ProtobufPropertyType.MapType mapType, boolean nullCheck, boolean cast) {
        var bodyWriter = nullCheck ? writer.printIfStatement("%s != null".formatted(accessor)) : writer;
        var localVariableName = "%sEntry".formatted(name); // Prevent shadowing
        try(var forWriter = bodyWriter.printForEachStatement(localVariableName, accessor + ".entrySet()")) {
            var methodName = ProtobufSizeMethodGenerator.getMapPropertyMethodName(name);
            forWriter.println("%s.writeObject(%s, %s(%s%s));".formatted(OUTPUT_OBJECT_PARAMETER, index, methodName, cast ? "(java.util.Map.Entry) " : "", localVariableName));
            writeAnySerializer(classWriter, forWriter, 1, name, "%s.getKey()".formatted(localVariableName), mapType.keyType(), false, false, cast);
            writeAnySerializer(classWriter, forWriter, 2, name, "%s.getValue()".formatted(localVariableName), mapType.valueType(), true, true, true);
        }
        if(nullCheck) {
            bodyWriter.close();
        }
    }

    private void writeAnySerializer(ClassWriter classWriter, BodyWriter writer, int index, String name, String value, ProtobufPropertyType type, boolean checkObject, boolean reassignValue, boolean cast) {
        var writeMethod = getStreamMethodName(type.protobufType(), isEnum(type), false);
        var result = getPropertyVariables(name, value, type);
        if(!result.hasConverter()) {
            writeDirectSerializer(classWriter, writer, index, name, type, checkObject, reassignValue, result, writeMethod.orElse(null), cast);
        }else {
            writeConvertedSerializer(classWriter, writer, index, name, type, result, writeMethod.orElse(null), cast);
        }
    }

    private void writeDirectSerializer(ClassWriter classWriter, BodyWriter writer, int index, String name, ProtobufPropertyType type, boolean checkObject, boolean reassignValue, ProtobufPropertyVariables result, String writeMethod, boolean cast) {
        var toWrite = result.variables().getFirst().value();
        if(isEnum(type)) {
            var specName = getSpecFromObject(type.serializedType());
            writer.println("%s.%s(%s, %s.%s(%s%s));".formatted(OUTPUT_OBJECT_PARAMETER, Objects.requireNonNull(writeMethod), index, specName, name(), cast ? "(%s) ".formatted(getQualifiedName(type.serializedType())) : "", toWrite));
        } else if(writeMethod == null) {
            var specName = getSpecFromObject(type.accessorType());
            if(checkObject) {
                var checkedVariable = reassignValue ? name : toWrite;
                if(reassignValue) {
                    writer.printVariableDeclaration(checkedVariable, "%s%s".formatted(cast ? "(%s) ".formatted(getQualifiedName(type.serializedType())) : "", toWrite));
                }

                try(var nullCheck = writer.printIfStatement("%s != null".formatted(checkedVariable))) {
                    if(type.protobufType() != ProtobufType.GROUP) {
                        nullCheck.println("%s.writeObject(%s, %s.%s(%s));".formatted(OUTPUT_OBJECT_PARAMETER, index, specName, ProtobufSizeMethodGenerator.METHOD_NAME, checkedVariable));
                        nullCheck.println("%s.%s(%s, %s);".formatted(specName, name(), checkedVariable, OUTPUT_OBJECT_PARAMETER));
                    }else {
                        nullCheck.println("%s.%s(%s, %s, %s);".formatted(specName, name(), index, checkedVariable, OUTPUT_OBJECT_PARAMETER));
                    }
                }
            }else if(type.protobufType() != ProtobufType.GROUP) {
                writer.println("%s.writeObject(%s, %s.%s(%s));".formatted(OUTPUT_OBJECT_PARAMETER, index, specName, ProtobufSizeMethodGenerator.METHOD_NAME, toWrite));
                writer.println("%s.%s(%s, %s);".formatted(specName, name(), toWrite, OUTPUT_OBJECT_PARAMETER));
            }else {
                writeGroupSerializer(classWriter, writer, index, name, type, toWrite, specName);
            }
        } else {
            writer.println("%s.%s(%s, %s%s);".formatted(OUTPUT_OBJECT_PARAMETER, writeMethod, index, cast ? "(%s) ".formatted(type.protobufType().wrappedType().getName()) : "", toWrite));
        }
    }

    private void writeConvertedSerializer(ClassWriter classWriter, BodyWriter writer, int index, String name, ProtobufPropertyType type, ProtobufPropertyVariables result, String writeMethod, boolean cast) {
        String propertyName = null;
        var nestedWriters = new LinkedList<BodyWriter>();
        nestedWriters.add(writer);
        for(var i = 0; i < result.variables().size(); i++) {
            var variable = result.variables().get(i);
            nestedWriters.getLast().printVariableDeclaration(variable.name(), "%s%s".formatted(cast && i == 0 ? "(%s) ".formatted(getQualifiedName(variable.type())) : "", variable.value()));
            propertyName = name + (i == 0 ? "" : i - 1);
            if(!variable.primitive()) {
                var newWriter = nestedWriters.getLast().printIfStatement("%s != null".formatted(propertyName));
                nestedWriters.add(newWriter);
            }
        }

        if(isEnum(type)) {
            var specName = getSpecFromObject(type.serializedType());
            nestedWriters.getLast().println("%s.%s(%s, %s.%s(%s%s));".formatted(OUTPUT_OBJECT_PARAMETER, Objects.requireNonNull(writeMethod), index, specName, name(), cast ? "(%s) ".formatted(getQualifiedName(type.serializedType())) : "", propertyName));
        } else if(writeMethod == null) {
            var specType = result.variables().getLast().type();
            var specName = getSpecFromObject(specType);
            if(type.protobufType() != ProtobufType.GROUP) {
                nestedWriters.getLast().println("%s.writeObject(%s, %s.%s(%s));".formatted(OUTPUT_OBJECT_PARAMETER, index, specName, ProtobufSizeMethodGenerator.METHOD_NAME, propertyName));
                nestedWriters.getLast().println("%s.%s(%s, %s);".formatted(specName, name(), propertyName, OUTPUT_OBJECT_PARAMETER));
            }else {
                writeGroupSerializer(classWriter, nestedWriters.getLast(), index, name, type, propertyName, specName);
            }
        } else {
            nestedWriters.getLast().println("%s.%s(%s, %s%s);".formatted(OUTPUT_OBJECT_PARAMETER, writeMethod, index, cast ? "(%s) ".formatted(type.protobufType().wrappedType().getName()) : "", propertyName));
        }

        for (var i = nestedWriters.size() - 1; i >= 1; i--) {
            var nestedWriter = nestedWriters.get(i);
            nestedWriter.close();
        }
    }

    private void writeGroupSerializer(ClassWriter classWriter, BodyWriter writer, int index, String name, ProtobufPropertyType type, String propertyName, String specName) {
        if(isConcreteGroup(type)) {
            writer.println("%s.%s(%s, %s, %s);".formatted(specName, name(), index, propertyName, OUTPUT_OBJECT_PARAMETER));
        }else {
            var typeName = getSimpleName(type.deserializedType());
            var methodName = "encode" + typeName;
            writer.println("%s(%s, %s, %s);".formatted(methodName, index, propertyName, OUTPUT_OBJECT_PARAMETER));
            if(deferredGroupSerializers.add(typeName)) {
                deferredOperations.add(() -> writeRawGroupSerializer(classWriter, name, methodName, type));
            }
        }
    }

    private void writeRawGroupSerializer(ClassWriter classWriter, String name, String methodName, ProtobufPropertyType type) {
        var protoGroupEntry = name + "ProtoGroupEntry";
        try (var methodWriter = classWriter.printMethodDeclaration(List.of("private", "static"), "void", methodName, "int index, java.util.Map<Integer, Object> properties, ProtobufOutputStream %s".formatted(OUTPUT_OBJECT_PARAMETER))) {
            methodWriter.println("%s.writeGroupStart(index);".formatted(OUTPUT_OBJECT_PARAMETER));
            try (var forEachBody = methodWriter.printForEachStatement(protoGroupEntry, "properties.entrySet()")) {
                var propertyValueName = forEachBody.printVariableDeclaration(name + "ProtoGroupEntryValue", protoGroupEntry + ".getValue()");
                try (var nullCheck = forEachBody.printIfStatement(propertyValueName + " != null")) {
                    try (var switchBody = nullCheck.printSwitchStatement(protoGroupEntry + ".getKey()")) {
                        for (var entry : type.serializers().getLast().groupProperties().entrySet()) {
                            try (var switchCaseBody = switchBody.printSwitchBranch(String.valueOf(entry.getKey()))) {
                                var protoGroupPropertyEntry = "%s.getValue()".formatted(protoGroupEntry);
                                switch (entry.getValue().type()) {
                                    case ProtobufPropertyType.CollectionType collectionType -> {
                                        var collectionField = "((java.util.Collection) %s)".formatted(protoGroupPropertyEntry);
                                        writeRepeatedPropertySerializer(classWriter, switchCaseBody, entry.getKey(), name, collectionField, collectionType, entry.getValue().packed(), false, true);
                                    }
                                    case ProtobufPropertyType.MapType mapType -> {
                                        var mapField = "((java.util.Map<?, ?>) %s)".formatted(protoGroupPropertyEntry);
                                        writeMapSerializer(classWriter, switchCaseBody, entry.getKey(), name + entry.getKey(), mapField, mapType, false, true);
                                    }
                                    default -> writeAnySerializer(classWriter, switchCaseBody, entry.getKey(), name, protoGroupPropertyEntry, entry.getValue().type(), false, false, true);
                                }
                            }
                        }
                    }
                }
            }
            methodWriter.println("%s.writeGroupEnd(index);".formatted(OUTPUT_OBJECT_PARAMETER));
        }
    }

    private boolean isConcreteGroup(ProtobufPropertyType type) {
        return type.serializedType() instanceof DeclaredType serializedType
                && serializedType.asElement().getAnnotation(ProtobufGroup.class) != null;
    }

    // Returns the method to use to deserialize a property from ProtobufInputStream
    private Optional<String> getStreamMethodName(ProtobufType protobufType, boolean isEnum, boolean packed) {
        var result = switch (protobufType) {
            case STRING -> "writeString";
            case UNKNOWN -> throw new IllegalArgumentException("Internal bug: unknown types should not reach getSerializerStreamMethod");
            case OBJECT, GROUP -> isEnum ? "writeInt32" : null;
            case BYTES -> "writeBytes";
            case BOOL -> "writeBool";
            case INT32, SINT32 -> "writeInt32";
            case UINT32 -> "writeUInt32";
            case MAP -> throw new IllegalArgumentException("Internal bug: map types should not reach getSerializerStreamMethod");
            case FLOAT -> "writeFloat";
            case DOUBLE -> "writeDouble";
            case FIXED32, SFIXED32 -> "writeFixed32";
            case INT64, SINT64 -> "writeInt64";
            case UINT64 -> "writeUInt64";
            case FIXED64, SFIXED64 -> "writeFixed64";
        };

        if(result != null && packed) {
            return Optional.of(result + "Packed");
        }

        return Optional.ofNullable(result);
    }

    protected boolean isEnum(ProtobufPropertyType type) {
        return type instanceof ProtobufPropertyType.NormalType normalType
                && normalType.deserializedParameterType() instanceof DeclaredType declaredType
                && declaredType.asElement().getKind() == ElementKind.ENUM;
    }
}

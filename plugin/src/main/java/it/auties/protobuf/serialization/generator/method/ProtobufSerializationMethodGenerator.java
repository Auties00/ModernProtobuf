package it.auties.protobuf.serialization.generator.method;

import it.auties.protobuf.serialization.model.object.ProtobufObjectElement;
import it.auties.protobuf.serialization.model.property.ProtobufPropertyElement;
import it.auties.protobuf.serialization.model.property.ProtobufPropertyType;
import it.auties.protobuf.serialization.support.JavaWriter.BodyWriter;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter;
import it.auties.protobuf.stream.ProtobufOutputStream;

import javax.lang.model.element.ElementKind;
import javax.lang.model.type.DeclaredType;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

public class ProtobufSerializationMethodGenerator extends ProtobufMethodGenerator {
    private static final String DEFAULT_OUTPUT_STREAM_NAME = "outputStream";
    private static final String DEFAULT_PARAMETER_NAME = "protoInputObject";

    public ProtobufSerializationMethodGenerator(ProtobufObjectElement element) {
        super(element);
    }

    @Override
    protected void doInstrumentation(ClassWriter classWriter, ClassWriter.MethodWriter writer) {
        if (message.isEnum()) {
            createEnumSerializer(writer);
        } else {
            createMessageSerializer(writer);
        }
    }

    private void createEnumSerializer(ClassWriter.MethodWriter writer) {
        try(var ifWriter = writer.printIfStatement("%s == null".formatted(DEFAULT_PARAMETER_NAME))) {
            ifWriter.printReturn("null");
        }

        var fieldName = message.enumMetadata()
                .orElseThrow(() -> new NoSuchElementException("Missing metadata from enum"))
                .field()
                .getSimpleName();
        writer.printReturn("%s.%s".formatted(DEFAULT_PARAMETER_NAME, fieldName));
    }

    private void createMessageSerializer(ClassWriter.MethodWriter writer) {
        try(var ifWriter = writer.printIfStatement("%s == null".formatted(DEFAULT_PARAMETER_NAME))) {
            ifWriter.printReturn("");
        }

        createRequiredPropertiesNullCheck(writer);
        for(var property : message.properties()) {
            switch (property.type()) {
                case ProtobufPropertyType.CollectionType collectionType -> writeRepeatedPropertySerializer(writer, property, collectionType);
                case ProtobufPropertyType.MapType mapType -> writeMapSerializer(writer, property, mapType);
                default -> writeSerializer(writer, property.index(), property.name(), getAccessorCall(property), property.type(), true, true);
            }
        }
    }

    private void createRequiredPropertiesNullCheck(ClassWriter.MethodWriter writer) {
        message.properties()
                .stream()
                .filter(ProtobufPropertyElement::required)
                .forEach(entry -> writer.println("Objects.requireNonNull(%s, \"Missing required property: %s\");".formatted(getAccessorCall(entry), entry.name())));
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
        return message.isEnum() ? List.of(message.element().getSimpleName().toString()) :
                List.of(message.element().getSimpleName().toString(), ProtobufOutputStream.class.getSimpleName());
    }

    @Override
    protected List<String> parametersNames() {
        return message.isEnum() ? List.of(DEFAULT_PARAMETER_NAME) :
                List.of(DEFAULT_PARAMETER_NAME, DEFAULT_OUTPUT_STREAM_NAME);
    }

    private String getAccessorCall(ProtobufPropertyElement property) {
        return getAccessorCall(DEFAULT_PARAMETER_NAME, property);
    }

    private void writeRepeatedPropertySerializer(ClassWriter.MethodWriter writer, ProtobufPropertyElement property, ProtobufPropertyType.CollectionType collectionType) {
        var accessorCall = getAccessorCall(property);
        try(var ifWriter = writer.printIfStatement("%s != null".formatted(accessorCall))) {
            var localVariableName = "%sEntry".formatted(property.name()); // Prevent shadowing
            try(var forEachWriter = ifWriter.printForEachStatement(localVariableName, accessorCall)) {
                writeSerializer(forEachWriter, property.index(), property.name(), localVariableName, collectionType.value(), true, false);
            }
        }
    }

    private void writeMapSerializer(ClassWriter.MethodWriter writer, ProtobufPropertyElement property, ProtobufPropertyType.MapType mapType) {
        var accessorCall = getAccessorCall(property);
        try(var ifWriter = writer.printIfStatement("%s != null".formatted(accessorCall))) {
            var localVariableName = "%sEntry".formatted(property.name()); // Prevent shadowing
            try(var forWriter = ifWriter.printForEachStatement(localVariableName, accessorCall + ".entrySet()")) {
                var methodName = ProtobufSizeMethodGenerator.getMapPropertyMethodName(property);
                forWriter.println("%s.writeObject(%s, %s(%s));".formatted(DEFAULT_OUTPUT_STREAM_NAME, property.index(), methodName, localVariableName));
                writeSerializer(forWriter, 1, property.name(), "%s.getKey()".formatted(localVariableName), mapType.keyType(), false, false);
                writeSerializer(forWriter, 2, property.name(), "%s.getValue()".formatted(localVariableName), mapType.valueType(), false, true);
            }
        }
    }

    private void writeSerializer(BodyWriter writer, int index, String name, String value, ProtobufPropertyType type, boolean checkObject, boolean reassignValue) {
        var writeMethod = getSerializerStreamMethod(type);
        var result = getVariables(name, value, type);
        if(!result.hasConverter()) {
            var toWrite = result.variables().getFirst().value();
            if(isEnum(type)) {
                var specName = getSpecFromObject(type.serializedType());
                writer.println("%s.%s(%s, %s.%s(%s));".formatted(DEFAULT_OUTPUT_STREAM_NAME, writeMethod.orElseThrow(), index, specName, name(), toWrite));
            } else if(writeMethod.isEmpty()) {
                var specName = getSpecFromObject(type.accessorType());
                if(checkObject) {
                    var checkedVariable = reassignValue ? name : toWrite;
                    if(reassignValue) {
                        writer.printVariableDeclaration(checkedVariable, toWrite);
                    }

                    try(var nullCheck = writer.printIfStatement("%s != null".formatted(checkedVariable))) {
                        nullCheck.println("%s.writeObject(%s, %s.%s(%s));".formatted(DEFAULT_OUTPUT_STREAM_NAME, index, specName, ProtobufSizeMethodGenerator.METHOD_NAME, checkedVariable));
                        nullCheck.println("%s.%s(%s, %s);".formatted(specName, name(), checkedVariable, DEFAULT_OUTPUT_STREAM_NAME));
                    }
                }else {
                    writer.println("%s.writeObject(%s, %s.%s(%s));".formatted(DEFAULT_OUTPUT_STREAM_NAME, index, specName, ProtobufSizeMethodGenerator.METHOD_NAME, toWrite));
                    writer.println("%s.%s(%s, %s);".formatted(specName, name(), toWrite, DEFAULT_OUTPUT_STREAM_NAME));
                }
            } else {
                writer.println("%s.%s(%s, %s);".formatted(DEFAULT_OUTPUT_STREAM_NAME, writeMethod.get(), index, toWrite));
            }
        }else {
            String propertyName = null;
            var nestedWriters = new LinkedList<BodyWriter>();
            nestedWriters.add(writer);
            for(var i = 0; i < result.variables().size(); i++) {
                var variable = result.variables().get(i);
                nestedWriters.getLast().printVariableDeclaration(variable.name(), variable.value());
                propertyName = name + (i == 0 ? "" : i - 1);
                if(!variable.primitive()) {
                    var newWriter = nestedWriters.getLast().printIfStatement("%s != null".formatted(propertyName));
                    nestedWriters.add(newWriter);
                }
            }

            if(isEnum(type)) {
                var specName = getSpecFromObject(type.serializedType());
                nestedWriters.getLast().println("%s.%s(%s, %s.%s(%s));".formatted(DEFAULT_OUTPUT_STREAM_NAME, writeMethod.orElseThrow(), index, specName, name(), propertyName));
            } else if(writeMethod.isEmpty()) {
                var specType = result.variables().getLast().type();
                var specName = getSpecFromObject(specType);
                nestedWriters.getLast().println("%s.writeObject(%s, %s.%s(%s));".formatted(DEFAULT_OUTPUT_STREAM_NAME, index, specName, ProtobufSizeMethodGenerator.METHOD_NAME, propertyName));
                nestedWriters.getLast().println("%s.%s(%s, %s);".formatted(specName, name(), propertyName, DEFAULT_OUTPUT_STREAM_NAME));
            } else {
                nestedWriters.getLast().println("%s.%s(%s, %s);".formatted(DEFAULT_OUTPUT_STREAM_NAME, writeMethod.get(), index, propertyName));
            }

            for (var i = nestedWriters.size() - 1; i >= 1; i--) {
                var nestedWriter = nestedWriters.get(i);
                nestedWriter.close();
            }
        }
    }

    // Returns the method to use to deserialize a property from ProtobufInputStream
    private Optional<String> getSerializerStreamMethod(ProtobufPropertyType type) {
        return Optional.ofNullable(switch (type.protobufType()) {
            case STRING -> "writeString";
            case OBJECT -> isEnum(type) ? "writeInt32" : null;
            case BYTES -> "writeBytes";
            case BOOL -> "writeBool";
            case INT32, SINT32 -> "writeInt32";
            case UINT32 -> "writeUInt32";
            case FLOAT -> "writeFloat";
            case DOUBLE -> "writeDouble";
            case FIXED32, SFIXED32 -> "writeFixed32";
            case INT64, SINT64 -> "writeInt64";
            case UINT64 -> "writeUInt64";
            case FIXED64, SFIXED64 -> "writeFixed64";
            default -> throw new IllegalStateException("Unexpected value: " + type.protobufType());
        });
    }

    protected boolean isEnum(ProtobufPropertyType type) {
        return type instanceof ProtobufPropertyType.NormalType normalType
                && normalType.deserializedType() instanceof DeclaredType declaredType
                && declaredType.asElement().getKind() == ElementKind.ENUM;
    }
}

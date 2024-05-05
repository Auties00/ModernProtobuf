package it.auties.protobuf.serialization.generator.method;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.serialization.converter.ProtobufSerializerElement;
import it.auties.protobuf.serialization.object.ProtobufObjectElement;
import it.auties.protobuf.serialization.property.ProtobufPropertyElement;
import it.auties.protobuf.serialization.property.ProtobufPropertyType;
import it.auties.protobuf.serialization.support.JavaWriter.BodyWriter;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter;
import it.auties.protobuf.stream.ProtobufOutputStream;

import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import java.lang.reflect.Method;
import java.util.*;

public class ProtobufSerializationMethodGenerator extends ProtobufMethodGenerator {
    private static final String DEFAULT_OUTPUT_STREAM_NAME = "outputStream";
    private static final String DEFAULT_PARAMETER_NAME = "protoInputObject";

    public ProtobufSerializationMethodGenerator(ProtobufObjectElement element) {
        super(element);
    }

    @Override
    protected void doInstrumentation(ClassWriter.MethodWriter writer) {
        try(var ifWriter = writer.printIfStatement("%s == null".formatted(DEFAULT_PARAMETER_NAME))) {
            ifWriter.printReturn("null");
        }

        if (message.isEnum()) {
            createEnumSerializer(writer);
        } else {
            createMessageSerializer(writer);
        }
    }

    private void createEnumSerializer(ClassWriter.MethodWriter writer) {
        var fieldName = message.enumMetadata()
                .orElseThrow(() -> new NoSuchElementException("Missing metadata from enum"))
                .field()
                .getSimpleName();
        writer.printReturn("%s.%s".formatted(DEFAULT_PARAMETER_NAME, fieldName));
    }

    private void createMessageSerializer(ClassWriter.MethodWriter writer) {
        createRequiredPropertiesNullCheck(writer);
        writer.printVariableDeclaration(DEFAULT_OUTPUT_STREAM_NAME, "new ProtobufOutputStream()");
        for(var property : message.properties()) {
            switch (property.type()) {
                case ProtobufPropertyType.CollectionType collectionType -> writeRepeatedPropertySerializer(writer, property, collectionType);
                case ProtobufPropertyType.MapType mapType -> writeMapSerializer(writer, property, mapType);
                default -> writeSerializer(writer, property.index(), property.name(), getAccessorCall(property), property.type(), DEFAULT_OUTPUT_STREAM_NAME);
            }
        }
        writer.printReturn("%s.toByteArray()".formatted(DEFAULT_OUTPUT_STREAM_NAME));
    }

    private void createRequiredPropertiesNullCheck(ClassWriter.MethodWriter writer) {
        message.properties()
                .stream()
                .filter(ProtobufPropertyElement::required)
                .forEach(entry -> writer.println("Objects.requireNonNull(%s, \"Missing required property: %s\");".formatted(getAccessorCall(entry), entry.name())));
    }

    @Override
    public boolean shouldInstrument() {
        return !message.isEnum() || !hasEnumSerializationMethod();
    }

    private boolean hasEnumSerializationMethod() {
        return message.element().getEnclosedElements()
                .stream()
                .anyMatch(this::isEnumSerializationMethod);
    }

    private boolean isEnumSerializationMethod(Element entry) {
        return entry instanceof ExecutableElement executableElement
                && executableElement.getSimpleName().contentEquals(name())
                && executableElement.getParameters().isEmpty();
    }

    @Override
    protected List<String> modifiers() {
        return List.of("public", "static");
    }

    @Override
    protected String returnType() {
        return message.isEnum() ? "Integer" : "byte[]";
    }

    @Override
    public String name() {
        return "encode";
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
        return switch (property.accessor()) {
            case ExecutableElement executableElement -> "%s.%s()".formatted(DEFAULT_PARAMETER_NAME, executableElement.getSimpleName());
            case VariableElement variableElement -> "%s.%s".formatted(DEFAULT_PARAMETER_NAME, variableElement.getSimpleName());
            default -> throw new IllegalStateException("Unexpected value: " + property.accessor());
        };
    }

    private void writeRepeatedPropertySerializer(ClassWriter.MethodWriter writer, ProtobufPropertyElement property, ProtobufPropertyType.CollectionType collectionType) {
        var accessorCall = getAccessorCall(property);
        try(var ifWriter = writer.printIfStatement("%s != null".formatted(accessorCall))) {
            var localVariableName = "%sEntry".formatted(property.name()); // Prevent shadowing
            try(var forEachWriter = ifWriter.printForEachStatement(localVariableName, accessorCall)) {
                writeSerializer(forEachWriter, property.index(), property.name(), localVariableName, collectionType.value(), DEFAULT_OUTPUT_STREAM_NAME);
            }
        }
    }

    private void writeMapSerializer(ClassWriter.MethodWriter writer, ProtobufPropertyElement property, ProtobufPropertyType.MapType mapType) {
        var accessorCall = getAccessorCall(property);
        try(var ifWriter = writer.printIfStatement("%s != null".formatted(accessorCall))) {
            var localStreamName = "%sOutputStream".formatted(property.name()); // Prevent shadowing
            var localVariableName = "%sEntry".formatted(property.name()); // Prevent shadowing
            try(var forWriter = ifWriter.printForEachStatement(localVariableName, accessorCall + ".entrySet()")) {
                forWriter.printVariableDeclaration(localStreamName, "new ProtobufOutputStream()");
                writeSerializer(forWriter, 1, property.name(), "%s.getKey()".formatted(localVariableName), mapType.keyType(), localStreamName);
                writeSerializer(forWriter, 2, property.name(), "%s.getValue()".formatted(localVariableName), mapType.valueType(), localStreamName);
                forWriter.println("%s.writeBytes(%s, %s.toByteArray());".formatted(DEFAULT_OUTPUT_STREAM_NAME, property.index(), localStreamName));
            }
        }
    }

    private void writeSerializer(BodyWriter writer, int index, String name, String caller, ProtobufPropertyType type, String streamName) {
        var writeMethod = getSerializerStreamMethods(type);
        var result = getVariables(name, caller, type);
        if(!result.hasConverter()) {
            var toWrite = result.variables().getFirst().value();
            var toWriteConverted = type.protobufType() != ProtobufType.OBJECT ? toWrite : "%s.encode(%s)".formatted(getSpecFromObject(type.implementationType()), toWrite);
            writer.println("%s.%s(%s, %s);".formatted(streamName, writeMethod.getName(), index, toWriteConverted));
            return;
        }

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

        if (type.protobufType() != ProtobufType.OBJECT) {
            nestedWriters.getLast().println("%s.%s(%s, %s);".formatted(streamName, writeMethod.getName(), index, propertyName));
        } else {
            var specType = result.variables().getLast().type();
            var specName = getSpecFromObject(specType);
            var toWriteConverted = "%s.encode(%s)".formatted(specName, propertyName);
            nestedWriters.getLast().println("%s.%s(%s, %s);".formatted(streamName, writeMethod.getName(), index, toWriteConverted));
        }

        for (var i = nestedWriters.size() - 1; i >= 1; i--) {
            var nestedWriter = nestedWriters.get(i);
            nestedWriter.close();
        }
    }

    private ProtobufPropertyVariables getVariables(String name, String caller, ProtobufPropertyType type) {
        var serializers = type.serializers();
        var variable = new ProtobufPropertyVariable(type.implementationType(), name, caller, type.isPrimitive());
        if (serializers.isEmpty()) {
            return new ProtobufPropertyVariables(false, List.of(variable));
        }

        var results = new ArrayList<ProtobufPropertyVariable>();
        results.add(variable);
        for (var index = 0; index < serializers.size(); index++) {
            var serializerElement = serializers.get(index);
            var lastInitializer = index == 0 ? name : name + (index - 1);
            var convertedInitializer = getConvertedInitializer(serializerElement, lastInitializer);
            var currentVariable = new ProtobufPropertyVariable(
                    serializerElement.returnType(),
                    name + index, convertedInitializer,
                    serializerElement.returnType().getKind().isPrimitive()
            );
            results.add(currentVariable);
        }

        return new ProtobufPropertyVariables(true, results);
    }

    private record ProtobufPropertyVariables(boolean hasConverter, List<ProtobufPropertyVariable> variables) {

    }

    private record ProtobufPropertyVariable(TypeMirror type, String name, String value, boolean primitive) {

    }

    private String getConvertedInitializer(ProtobufSerializerElement serializerElement, String lastInitializer) {
        if (serializerElement.delegate().getKind() == ElementKind.CONSTRUCTOR) {
            var converterWrapperClass = (TypeElement) serializerElement.delegate().getEnclosingElement();
            return "new %s(%s)".formatted(converterWrapperClass.getQualifiedName(), lastInitializer);
        }

        if (serializerElement.delegate().getModifiers().contains(Modifier.STATIC)) {
            var converterWrapperClass = (TypeElement) serializerElement.delegate().getEnclosingElement();
            return "%s.%s(%s)".formatted(converterWrapperClass.getQualifiedName(), serializerElement.delegate().getSimpleName(), lastInitializer);
        }

        return "%s.%s()".formatted(lastInitializer, serializerElement.delegate().getSimpleName());
    }


    // Returns the method to use to deserialize a property from ProtobufInputStream
    private Method getSerializerStreamMethods(ProtobufPropertyType type) {
        try {
            var clazz = ProtobufOutputStream.class;
            return switch (type.protobufType()) {
                case STRING ->
                        isRepeatedWithoutConversion(type) ? clazz.getMethod("writeString", int.class, Collection.class) : clazz.getMethod("writeString", int.class, String.class);
                case OBJECT ->
                        type.isEnum() ? clazz.getMethod("writeInt32", int.class, Integer.class) : clazz.getMethod("writeBytes", int.class, byte[].class);
                case BYTES ->
                        isRepeatedWithoutConversion(type) ? clazz.getMethod("writeBytes", int.class, Collection.class) : clazz.getMethod("writeBytes", int.class, byte[].class);
                case BOOL ->
                        isRepeatedWithoutConversion(type) ? clazz.getMethod("writeBool", int.class, Collection.class) : clazz.getMethod("writeBool", int.class, Boolean.class);
                case INT32, SINT32 ->
                        isRepeatedWithoutConversion(type) ? clazz.getMethod("writeInt32", int.class, Collection.class) : clazz.getMethod("writeInt32", int.class, Integer.class);
                case UINT32 ->
                        isRepeatedWithoutConversion(type) ? clazz.getMethod("writeUInt32", int.class, Collection.class) : clazz.getMethod("writeUInt32", int.class, Integer.class);
                case FLOAT ->
                        isRepeatedWithoutConversion(type) ? clazz.getMethod("writeFloat", int.class, Collection.class) : clazz.getMethod("writeFloat", int.class, Float.class);
                case DOUBLE ->
                        isRepeatedWithoutConversion(type) ? clazz.getMethod("writeDouble", int.class, Collection.class) : clazz.getMethod("writeDouble", int.class, Double.class);
                case FIXED32, SFIXED32 ->
                        isRepeatedWithoutConversion(type) ? clazz.getMethod("writeFixed32", int.class, Collection.class) : clazz.getMethod("writeFixed32", int.class, Integer.class);
                case INT64, SINT64 ->
                        isRepeatedWithoutConversion(type) ? clazz.getMethod("writeInt64", int.class, Collection.class) : clazz.getMethod("writeInt64", int.class, Long.class);
                case UINT64 ->
                        isRepeatedWithoutConversion(type) ? clazz.getMethod("writeUInt64", int.class, Collection.class) : clazz.getMethod("writeUInt64", int.class, Long.class);
                case FIXED64, SFIXED64 ->
                        isRepeatedWithoutConversion(type) ? clazz.getMethod("writeFixed64", int.class, Collection.class) : clazz.getMethod("writeFixed64", int.class, Long.class);
                default -> throw new IllegalStateException("Unexpected value: " + type.protobufType());
            };
        }catch (NoSuchMethodException exception) {
            throw new RuntimeException("Missing delegate method", exception);
        }
    }

    private boolean isRepeatedWithoutConversion(ProtobufPropertyType type) {
        return type instanceof ProtobufPropertyType.CollectionType && type.serializers().isEmpty();
    }
}

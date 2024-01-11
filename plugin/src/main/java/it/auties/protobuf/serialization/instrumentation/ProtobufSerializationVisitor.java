package it.auties.protobuf.serialization.instrumentation;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.serialization.converter.ProtobufSerializerElement;
import it.auties.protobuf.serialization.object.ProtobufMessageElement;
import it.auties.protobuf.serialization.property.ProtobufPropertyStub;
import it.auties.protobuf.serialization.property.ProtobufPropertyType;
import it.auties.protobuf.stream.ProtobufOutputStream;

import javax.lang.model.element.*;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;

public class ProtobufSerializationVisitor extends ProtobufInstrumentationVisitor {
    private static final String DEFAULT_OUTPUT_STREAM_NAME = "outputStream";
    private static final String DEFAULT_PARAMETER_NAME = "protoInputObject";

    public ProtobufSerializationVisitor(ProtobufMessageElement element, PrintWriter writer) {
        super(element, writer);
    }

    @Override
    protected void doInstrumentation() {
        writer.println("      if(%s == null) {".formatted(DEFAULT_PARAMETER_NAME));
        writer.println("         return null;");
        writer.println("      }");
        if(message.isEnum()) {
            createEnumSerializer();
        }else {
            createMessageSerializer();
        }
    }

    private void createEnumSerializer() {
        var fieldName = message.enumMetadata()
                .orElseThrow(() -> new NoSuchElementException("Missing metadata from enum"))
                .field()
                .getSimpleName();
        writer.println("      return %s.%s;".formatted(DEFAULT_PARAMETER_NAME, fieldName));
    }

    private void createMessageSerializer() {
        createRequiredPropertiesNullCheck();
        writer.println("      var %s = new ProtobufOutputStream();".formatted(DEFAULT_OUTPUT_STREAM_NAME));
        message.properties().forEach(this::writeProperty);
        writer.println("      return %s.toByteArray();".formatted(DEFAULT_OUTPUT_STREAM_NAME));
    }

    private void createRequiredPropertiesNullCheck() {
        message.properties()
                .stream()
                .filter(ProtobufPropertyStub::required)
                .forEach(entry -> writer.println("      Objects.requireNonNull(%s, \"Missing required property: %s\");".formatted(getAccessorCall(entry), entry.name())));
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

    // Writes a property to the output stream
    private void writeProperty(ProtobufPropertyStub property) {
        switch (property.type()) {
            case ProtobufPropertyType.CollectionType collectionType -> writeRepeatedPropertySerializer(property, collectionType);
            case ProtobufPropertyType.MapType mapType -> writeMapSerializer(property, mapType);
            default -> writeSerializer(property.index(), property.name(), getAccessorCall(property), property.type(), DEFAULT_OUTPUT_STREAM_NAME);
        }
    }

    private String getAccessorCall(ProtobufPropertyStub property) {
        return switch (property.accessor()) {
            case ExecutableElement executableElement -> "%s.%s()".formatted(DEFAULT_PARAMETER_NAME, executableElement.getSimpleName());
            case VariableElement variableElement -> "%s.%s".formatted(DEFAULT_PARAMETER_NAME, variableElement.getSimpleName());
            default -> throw new IllegalStateException("Unexpected value: " + property.accessor());
        };
    }

    private void writeRepeatedPropertySerializer(ProtobufPropertyStub property, ProtobufPropertyType.CollectionType collectionType) {
        var accessorCall = getAccessorCall(property);
        writer.println("      if(%s != null) {".formatted(accessorCall));
        var localVariableName = "%sEntry".formatted(property.name()); // Prevent shadowing
        writer.println("       for(var %s : %s) {".formatted(localVariableName, accessorCall));
        writeSerializer(property.index(), property.name(), localVariableName, collectionType.value(), DEFAULT_OUTPUT_STREAM_NAME);
        writer.println("       }");
        writer.println("      }");
    }

    private void writeMapSerializer(ProtobufPropertyStub property, ProtobufPropertyType.MapType mapType) {
        var accessorCall = getAccessorCall(property);
        writer.println("      if(%s != null) {".formatted(accessorCall));
        var localStreamName = "%sOutputStream".formatted(property.name()); // Prevent shadowing
        var localVariableName = "%sEntry".formatted(property.name()); // Prevent shadowing
        writer.println("            for(var %s : %s.entrySet()) {".formatted(localVariableName, accessorCall));
        writer.println("                var %s = new ProtobufOutputStream();".formatted(localStreamName));
        writeSerializer(1, property.name(), "%s.getKey()".formatted(localVariableName), mapType.keyType(), localStreamName);
        writeSerializer(2, property.name(), "%s.getValue()".formatted(localVariableName), mapType.valueType(), localStreamName);
        writer.println("                %s.writeBytes(%s, %s.toByteArray());".formatted(DEFAULT_OUTPUT_STREAM_NAME, property.index(), localStreamName));
        writer.println("            }");
        writer.println("      }");
    }

    private void writeSerializer(int index, String name, String caller, ProtobufPropertyType type, String streamName) {
        var writeMethod = getSerializerStreamMethods(type);
        var result = getVariables(name, caller, type);
        if(!result.hasConverter()) {
            var toWrite = result.variables().getFirst().value();
            var toWriteConverted = type.protobufType() != ProtobufType.OBJECT ? toWrite : "%s.encode(%s)".formatted(getSpecName(type.implementationType()), toWrite);
            writer.println("%s.%s(%s, %s);".formatted(streamName, writeMethod.getName(), index, toWriteConverted));
            return;
        }

        String propertyName = null;
        for(var i = 0; i < result.variables().size(); i++) {
            var variable = result.variables().get(i);
            writer.println(variable.value());
            propertyName = name + (i == 0 ? "" : i - 1);
            if(!variable.isPrimitive() && !variable.isOptional()) {
                writer.println("if(%s != null) {".formatted(propertyName));
            }
        }

        var toWriteConverted = type.protobufType() != ProtobufType.OBJECT ? propertyName : "%s.encode(%s)".formatted(getSpecName(type.implementationType()), propertyName);
        writer.println("%s.%s(%s, %s);".formatted(streamName, writeMethod.getName(), index, toWriteConverted));
        for(var variable : result.variables()) {
            if(!variable.isPrimitive() && !variable.isOptional()) {
                writer.println("}");
            }
        }
    }

    private ProtobufPropertyVariables getVariables(String name, String caller, ProtobufPropertyType type) {
        var serializers = type.serializers();
        var isPrimitive = type.isPrimitive();
        var isOptional = type instanceof ProtobufPropertyType.OptionalType;
        if (serializers.isEmpty()) {
            var variable = new ProtobufPropertyVariable(caller, isPrimitive, isOptional);
            return new ProtobufPropertyVariables(false, List.of(variable));
        }

        var results = new ArrayList<ProtobufPropertyVariable>();
        results.add(new ProtobufPropertyVariable("var %s = %s;".formatted(name, caller), isPrimitive, isOptional));
        var useMap = false;
        for (var index = 0; index < serializers.size(); index++) {
            var serializerElement = serializers.get(index);
            var lastInitializer = index == 0 ? name : name + (index - 1);
            var currentInitializer = name + index;
            var convertedInitializer = getConvertedInitializer(serializerElement, lastInitializer, useMap);
            results.add(new ProtobufPropertyVariable("var %s = %s;".formatted(currentInitializer, convertedInitializer), serializerElement.primitive(), serializerElement.optional()));
            useMap |= serializerElement.optional();
        }

        return new ProtobufPropertyVariables(true, results);
    }

    private record ProtobufPropertyVariables(boolean hasConverter, List<ProtobufPropertyVariable> variables) {

    }

    private record ProtobufPropertyVariable(String value, boolean isPrimitive, boolean isOptional) {

    }

    private String getConvertedInitializer(ProtobufSerializerElement serializerElement, String lastInitializer, boolean useMap) {
        if (serializerElement.element().getKind() == ElementKind.CONSTRUCTOR) {
            var converterWrapperClass = (TypeElement) serializerElement.element().getEnclosingElement();
            return "new %s(%s)".formatted(converterWrapperClass.getQualifiedName(), lastInitializer);
        }

        if (serializerElement.element().getModifiers().contains(Modifier.STATIC)) {
            var converterWrapperClass = (TypeElement) serializerElement.element().getEnclosingElement();
            if (!useMap) {
                return "%s.%s(%s)".formatted(converterWrapperClass.getQualifiedName(), serializerElement.element().getSimpleName(), lastInitializer);
            }

            var method = serializerElement.optional() ? "flatMap" : "map";
            return "%s.%s(lambdaArg -> %s.%s(lambdaArg))".formatted(lastInitializer, method, converterWrapperClass.getQualifiedName(), serializerElement.element().getSimpleName());
        }

        if (!useMap) {
            return "%s.%s(%s)".formatted(lastInitializer, serializerElement.element().getSimpleName(), String.join(", ", serializerElement.arguments()));
        }

        var method = serializerElement.optional() ? "flatMap" : "map";
        return "%s.%s(lambdaArg -> lambdaArg.%s(%s))".formatted(lastInitializer, method, serializerElement.element().getSimpleName(), String.join(", ", serializerElement.arguments()));
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
            throw new RuntimeException("Missing element method", exception);
        }
    }

    private boolean isRepeatedWithoutConversion(ProtobufPropertyType type) {
        return type instanceof ProtobufPropertyType.CollectionType && type.serializers().isEmpty();
    }
}

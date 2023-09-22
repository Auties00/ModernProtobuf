package it.auties.protobuf.serialization.instrumentation;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.serialization.converter.ProtobufSerializerElement;
import it.auties.protobuf.serialization.message.ProtobufMessageElement;
import it.auties.protobuf.serialization.property.ProtobufPropertyStub;
import it.auties.protobuf.stream.ProtobufOutputStream;

import javax.lang.model.element.*;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;

public class ProtobufSerializationVisitor extends ProtobufInstrumentationVisitor {
    public ProtobufSerializationVisitor(ProtobufMessageElement element, PrintWriter writer) {
        super(element, writer);
    }

    @Override
    protected void doInstrumentation() {
        writer.println("      if(protoInputObject == null) {");
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
        writer.println("      return protoInputObject.%s;".formatted(fieldName));
    }

    private void createMessageSerializer() {
        createRequiredPropertiesNullCheck();
        writer.println("      var outputStream = new ProtobufOutputStream();");
        message.properties().forEach(this::writeProperty);
        writer.println("      return outputStream.toByteArray();");
    }

    private void createRequiredPropertiesNullCheck() {
        message.properties()
                .stream()
                .filter(ProtobufPropertyStub::required)
                .forEach(entry -> writer.println("      Objects.requireNonNull(protoInputObject.%s(), \"Missing required property: %s\");".formatted(entry.name(), entry.name())));
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
        return List.of("protoInputObject");
    }

    // Writes a property to the output stream
    private void writeProperty(ProtobufPropertyStub property) {
        if (property.repeated()) {
            writeRepeatedPropertySerializer(property);
        } else {
            writeAnyPropertySerializer(property, null);
        }
    }

    private void writeRepeatedPropertySerializer(ProtobufPropertyStub property) {
        writer.println("      if(protoInputObject.%s() != null) {".formatted(property.name()));
        var localVariableName = "%sEntry".formatted(property.name()); // Prevent shadowing
        writer.println("       for(var %s : protoInputObject.%s()) {".formatted(localVariableName, property.name()));
        writeAnyPropertySerializer(property, localVariableName);
        writer.println("       }");
        writer.println("      }");
    }

    private void writeAnyPropertySerializer(ProtobufPropertyStub property, String overridePropertyName) {
        var writeMethod = getSerializerStreamMethod(property);
        var result = getVariables(property, overridePropertyName);
        if(!result.converter()) {
            var toWrite = result.variables().get(0).value();
            var toWriteConverted = property.type().protobufType() != ProtobufType.OBJECT ? toWrite : "%s.encode(%s)".formatted(getSpecName(property.type().implementationType()), toWrite);
            writer.println("outputStream.%s(%s, %s);".formatted(writeMethod.getName(), property.index(), toWriteConverted));
            return;
        }

        String propertyName = null;
        for(var index = 0; index < result.variables().size(); index++) {
            var variable = result.variables().get(index);
            writer.println(variable.value());
            propertyName = property.name() + (index == 0 ? "" : index - 1);
            if(!variable.primitive()) {
                writer.println("if(%s != null) {".formatted(propertyName));
            }
        }

        var toWriteConverted = property.type().protobufType() != ProtobufType.OBJECT ? propertyName : "%s.encode(%s)".formatted(getSpecName(property.type().implementationType()), propertyName);
        writer.println("outputStream.%s(%s, %s);".formatted(writeMethod.getName(), property.index(), toWriteConverted));
        for(var variable : result.variables()) {
            if(!variable.primitive()) {
                writer.println("}");
            }
        }
    }

    private ProtobufPropertyVariables getVariables(ProtobufPropertyStub property, String overridePropertyName) {
        var initializer = overridePropertyName != null ? overridePropertyName : "protoInputObject.%s()".formatted(property.accessor().getSimpleName());
        var converter = !property.type().serializers().isEmpty();
        if (!converter) {
            return new ProtobufPropertyVariables(false, List.of(new ProtobufPropertyVariable(initializer, property.type().fieldType().getKind().isPrimitive())));
        }

        var results = new ArrayList<ProtobufPropertyVariable>();
        results.add(new ProtobufPropertyVariable("var %s = %s;".formatted(property.name(), initializer), property.type().fieldType().getKind().isPrimitive()));
        var useMap = false;
        var serializers = property.type().serializers();
        for (var index = 0; index < serializers.size(); index++) {
            var serializerElement = serializers.get(index);
            var lastInitializer = index == 0 ? property.name() : property.name() + (index - 1);
            var currentInitializer = property.name() + index;
            var convertedInitializer = getConvertedInitializer(serializerElement, lastInitializer, useMap);
            results.add(new ProtobufPropertyVariable("var %s = %s;".formatted(currentInitializer, convertedInitializer), serializerElement.primitive()));
            useMap |= serializerElement.optional();
        }

        return new ProtobufPropertyVariables(true, results);
    }

    private record ProtobufPropertyVariables(boolean converter, List<ProtobufPropertyVariable> variables) {

    }

    private record ProtobufPropertyVariable(String value, boolean primitive) {

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
    private Method getSerializerStreamMethod(ProtobufPropertyStub annotation) {
        try {
            var clazz = ProtobufOutputStream.class;
            return switch (annotation.type().protobufType()) {
                case STRING ->
                        isConcreteRepeated(annotation) ? clazz.getMethod("writeString", int.class, Collection.class) : clazz.getMethod("writeString", int.class, String.class);
                case OBJECT ->
                        annotation.type().isEnum() ? clazz.getMethod("writeInt32", int.class, Integer.class) : clazz.getMethod("writeBytes", int.class, byte[].class);
                case BYTES ->
                        isConcreteRepeated(annotation) ? clazz.getMethod("writeBytes", int.class, Collection.class) : clazz.getMethod("writeBytes", int.class, byte[].class);
                case BOOL ->
                        isConcreteRepeated(annotation) ? clazz.getMethod("writeBool", int.class, Collection.class) : clazz.getMethod("writeBool", int.class, Boolean.class);
                case INT32, SINT32 ->
                        isConcreteRepeated(annotation) ? clazz.getMethod("writeInt32", int.class, Collection.class) : clazz.getMethod("writeInt32", int.class, Integer.class);
                case UINT32 ->
                        isConcreteRepeated(annotation) ? clazz.getMethod("writeUInt32", int.class, Collection.class) : clazz.getMethod("writeUInt32", int.class, Integer.class);
                case FLOAT ->
                        isConcreteRepeated(annotation) ? clazz.getMethod("writeFloat", int.class, Collection.class) : clazz.getMethod("writeFloat", int.class, Float.class);
                case DOUBLE ->
                        isConcreteRepeated(annotation) ? clazz.getMethod("writeDouble", int.class, Collection.class) : clazz.getMethod("writeDouble", int.class, Double.class);
                case FIXED32, SFIXED32 ->
                        isConcreteRepeated(annotation) ? clazz.getMethod("writeFixed32", int.class, Collection.class) : clazz.getMethod("writeFixed32", int.class, Integer.class);
                case INT64, SINT64 ->
                        isConcreteRepeated(annotation) ? clazz.getMethod("writeInt64", int.class, Collection.class) : clazz.getMethod("writeInt64", int.class, Long.class);
                case UINT64 ->
                        isConcreteRepeated(annotation) ? clazz.getMethod("writeUInt64", int.class, Collection.class) : clazz.getMethod("writeUInt64", int.class, Long.class);
                case FIXED64, SFIXED64 ->
                        isConcreteRepeated(annotation) ? clazz.getMethod("writeFixed64", int.class, Collection.class) : clazz.getMethod("writeFixed64", int.class, Long.class);
            };
        }catch (NoSuchMethodException exception) {
            throw new RuntimeException("Missing element method", exception);
        }
    }

    private boolean isConcreteRepeated(ProtobufPropertyStub annotation) {
        return annotation.repeated() && annotation.type().serializers().isEmpty();
    }
}

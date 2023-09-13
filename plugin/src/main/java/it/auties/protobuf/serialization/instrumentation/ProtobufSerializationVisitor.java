package it.auties.protobuf.serialization.instrumentation;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.serialization.model.ProtobufMessageElement;
import it.auties.protobuf.serialization.model.ProtobufPropertyStub;
import it.auties.protobuf.stream.ProtobufOutputStream;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;

public class ProtobufSerializationVisitor extends ProtobufInstrumentationVisitor {
    public ProtobufSerializationVisitor(ProtobufMessageElement element, PrintWriter writer) {
        super(element, writer);
    }

    @Override
    protected void doInstrumentation() {
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
        writer.println("        return protoInputObject.%s;".formatted(fieldName));
    }

    private void createMessageSerializer() {
        createRequiredPropertiesNullCheck();
        writer.println("        var outputStream = new ProtobufOutputStream();");
        message.properties().forEach(this::writeProperty);
        writer.println("        return outputStream.toByteArray();");
    }

    private void createRequiredPropertiesNullCheck() {
        message.properties()
                .stream()
                .filter(ProtobufPropertyStub::required)
                .forEach(entry -> writer.println("        Objects.requireNonNull(protoInputObject.%s(), \"Missing required property: %s\");".formatted(entry.name(), entry.name())));
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
        return message.isEnum() ? "int" : "byte[]";
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
            writeAnyPropertySerializer(property, null, 3);
        }
    }

    private void writeRepeatedPropertySerializer(ProtobufPropertyStub property) {
        writer.println("        if(protoInputObject.%s() != null) {".formatted(property.name()));
        var localVariableName = "%sEntry".formatted(property.name()); // Prevent shadowing
        writer.println("            for(var %s : protoInputObject.%s()) {".formatted(localVariableName, property.name()));
        writeAnyPropertySerializer(property, localVariableName, 5);
        writer.println("            }");
        writer.println("        }");
    }

    private void writeAnyPropertySerializer(ProtobufPropertyStub property, String overridePropertyName, int indentationLevel) {
        var hasConverter = !property.type().converters().isEmpty();
        if (hasConverter) {
            if(overridePropertyName != null) {
                writer.println("%sif(%s != null)".formatted("   ".repeat(indentationLevel), overridePropertyName));
            }else {
                writer.println("%sif(protoInputObject.%s() != null)".formatted("   ".repeat(indentationLevel), property.name()));
            };
        }

        var writeMethod = getSerializerStreamMethod(property);
        var writeValue = getWriteValue(property, overridePropertyName);
        var convertedValue = applyConverter(property, writeValue, hasConverter);
        var writeIndentation = "   ".repeat(hasConverter ? indentationLevel + 1 : indentationLevel);
        writer.println("%soutputStream.%s(%s, %s);".formatted(writeIndentation, writeMethod.getName(), property.index(), convertedValue));
    }

    private String getWriteValue(ProtobufPropertyStub property, String overridePropertyName) {
        var result = overridePropertyName != null ? overridePropertyName : "protoInputObject.%s()".formatted(property.name());
        if (property.protoType() != ProtobufType.OBJECT) {
            return result;
        }

        var spec = getSpecName(property.type().implementationType());
        return "%s.encode(%s)".formatted(spec, result);
    }

    private String applyConverter(ProtobufPropertyStub property, String writeValue, boolean hasConverter) {
        if (!hasConverter) {
            return writeValue;
        }

        var result = writeValue;
        for(var converter : property.type().converters()) {
            if(converter.serializer().getModifiers().contains(Modifier.STATIC)) {
                var converterWrapperClass = (TypeElement) converter.serializer().getEnclosingElement();
                result = "%s.%s(%s)".formatted(converterWrapperClass.getQualifiedName(), converter.serializer().getSimpleName(), result);
            }else {
                result = "%s.%s(%s)".formatted(result, converter.serializer().getSimpleName(), String.join(", ", converter.serializerArguments()));
            }
        }
        return result;
    }

    // Returns the method to use to deserialize a property from ProtobufInputStream
    private Method getSerializerStreamMethod(ProtobufPropertyStub annotation) {
        try {
            var clazz = ProtobufOutputStream.class;
            return switch (annotation.protoType()) {
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
            throw new RuntimeException("Missing serializer method", exception);
        }
    }

    private boolean isConcreteRepeated(ProtobufPropertyStub annotation) {
        return annotation.repeated() && annotation.type().converters().isEmpty();
    }
}

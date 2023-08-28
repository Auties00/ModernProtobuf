package it.auties.protobuf.serialization.instrumentation;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.serialization.model.ProtobufMessageElement;
import it.auties.protobuf.serialization.model.ProtobufPropertyStub;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public class ProtobufDeserializationVisitor extends ProtobufInstrumentationVisitor {
    public ProtobufDeserializationVisitor(ProtobufMessageElement element, PrintWriter writer) {
        super(element, writer);
    }

    @Override
    protected void doInstrumentation() {
        if (message.isEnum()) {
            createEnumDeserializer();
        }else {
            createMessageDeserializer();
        }
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
    protected String name() {
        return "decode";
    }

    @Override
    protected String returnType() {
        return message.isEnum() ? "Optional<%s>".formatted(message.element().getSimpleName())
                : message.element().getSimpleName().toString();
    }

    @Override
    protected List<String> parametersTypes() {
        return message.isEnum() ? List.of("int") : List.of("byte[]");
    }

    @Override
    protected List<String> parametersNames() {
        return message.isEnum() ? List.of("index") : List.of("input");
    }

    private void createEnumDeserializer() {
        var fieldName = message.enumMetadata()
                .orElseThrow(() -> new NoSuchElementException("Missing metadata from enum"))
                .field()
                .getSimpleName();
        writer.println("        return Arrays.stream(%s.values())".formatted(message.element().getSimpleName()));
        writer.println("                .filter(entry -> entry.%s == index)".formatted(fieldName));
        writer.println("                .findFirst();");
    }

    private void createMessageDeserializer() {
        // ProtobufInputStream stream = new ProtobufInputStream(var1);
        writer.println("        var inputStream = new ProtobufInputStream(input);");

        // [<type> var<index> = <defaultValue>, ...]
        for(var property : message.properties()) {
            var defaultValue = getPropertyDefaultValue(property);
            writer.println("        %s %s = %s;".formatted(property.type().fieldType(), property.name(), defaultValue));
        }

        // while(input.readTag())
        writer.println("        while(inputStream.readTag()) {");

        // switch(input.index())
        writer.println("            switch(inputStream.index()) {");
        var argumentsList = new ArrayList<String>();
        for(var property : message.properties()) {
            var readMethod = getDeserializerStreamMethod(property);
            var readValue = getReadValue(property, readMethod);
            var readFunction = getConvertedValue(property, readValue);
            var readAssignment = getReadAssignment(property, readFunction);
            writer.println("                case %s -> %s;".formatted(property.index(), readAssignment));
            argumentsList.add(property.name());
        }
        writer.println("                default -> inputStream.readBytes();");
        writer.println("            }");
        writer.println("        }");

        // Null check required properties
        message.properties()
                .stream()
                .filter(ProtobufPropertyStub::required)
                .forEach(this::checkRequiredProperty);

        // Return statement
        writer.println("        return new %s(%s);".formatted(message.element(), String.join(", ", argumentsList)));
    }

    private void checkRequiredProperty(ProtobufPropertyStub property) {
        if (!property.repeated()) {
            writer.println("        Objects.requireNonNull(%s, \"Missing required property: %s\");".formatted(property.name(), property.name()));
            return;
        }

        writer.println("        if(!%s.isEmpty())".formatted(property.name()));
        writer.println("            throw new NullPointerException(\"Missing required property: %s\");".formatted(property.name()));
    }

    private String getReadAssignment(ProtobufPropertyStub property, String readFunction) {
        if (!property.repeated()) {
            return "%s = %s".formatted(property.name(), readFunction);
        }

        var repeatedMethod = property.packed() ? "addAll" : "add";
        return "%s.%s(%s)".formatted(property.name(), repeatedMethod, readFunction);
    }

    private String getReadValue(ProtobufPropertyStub property, String readMethod) {
        var reader = "inputStream.%s()".formatted(readMethod);
        if (property.protoType() != ProtobufType.OBJECT) {
            return reader;
        }

        var specName = getSpecName(property.type().implementationType());
        if(property.type().isEnum()) {
            return "%s.decode(%s).orElse(null)".formatted(specName, reader);
        }

        return "%s.decode(%s)".formatted(specName, reader);
    }

    private String getConvertedValue(ProtobufPropertyStub property, String readValue) {
        var converter = property.type().converter();
        if (converter.isEmpty()) {
            return readValue;
        }

        var converterWrapperClass = property.type().implementationType();
        var converterMethodName = converter.get()
                .deserializer()
                .getSimpleName();
        return "%s.%s(%s)".formatted(converterWrapperClass, converterMethodName, readValue);
    }

    private String getPropertyDefaultValue(ProtobufPropertyStub property) {
        return switch (property.type().implementationType().getKind()) {
            case DECLARED, ARRAY -> {
                if (!property.repeated()) {
                    yield "null";
                }

                yield "new %s()".formatted(property.type().wrapperType());
            }
            case INT, CHAR, SHORT, BYTE -> "0";
            case BOOLEAN -> "false";
            case FLOAT -> "0f";
            case DOUBLE -> "0d";
            case LONG -> "0l";
            default -> throw new IllegalArgumentException("Unexpected type: " + property.type().implementationType());
        };
    }

    // Returns the method to use to deserialize a property from ProtobufInputStream
    private String getDeserializerStreamMethod(ProtobufPropertyStub property) {
        return property.type().isEnum() ? property.packed() ? "readInt32Packed" : "readInt32" : switch (property.protoType()) {
            case STRING -> "readString";
            case OBJECT, BYTES -> "readBytes";
            case BOOL -> property.packed() ? "readBoolPacked" : "readBool";
            case INT32, SINT32, UINT32 -> property.packed() ? "readInt32Packed" : "readInt32";
            case FLOAT -> property.packed() ? "readFloatPacked" : "readFloat";
            case DOUBLE -> property.packed() ? "readDoublePacked" : "readDouble";
            case FIXED32, SFIXED32 -> property.packed() ? "readFixed32Packed" : "readFixed32";
            case INT64, SINT64, UINT64 -> property.packed() ? "readInt64Packed" : "readInt64";
            case FIXED64, SFIXED64 -> property.packed() ? "readFixed64Packed" : "readFixed64";
        };
    }
}

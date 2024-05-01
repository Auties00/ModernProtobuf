package it.auties.protobuf.serialization.generator.method;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.serialization.object.ProtobufMessageElement;
import it.auties.protobuf.serialization.property.ProtobufPropertyElement;
import it.auties.protobuf.serialization.property.ProtobufPropertyType;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public class ProtobufDeserializationMethodGenerator extends ProtobufMethodGenerator {
    private static final String DEFAULT_STREAM_NAME = "inputStream";

    public ProtobufDeserializationMethodGenerator(ProtobufMessageElement element, PrintWriter writer) {
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
        writer.println("        if(input == null) {");
        writer.println("            return null;");
        writer.println("        }");
        // ProtobufInputStream stream = new ProtobufInputStream(var1);
        writer.println("        var %s = new ProtobufInputStream(input);".formatted(DEFAULT_STREAM_NAME));

        // [<implementationType> var<index> = <defaultValue>, ...]
        for(var property : message.properties()) {
            var type = switch (property.type()) {
                case ProtobufPropertyType.CollectionType collectionType -> collectionType.descriptorElementType();
                case ProtobufPropertyType.MapType mapType -> mapType.descriptorElementType();
                case ProtobufPropertyType.NormalType normalType -> normalType.implementationType();
            };
            writer.println("        %s %s = %s;".formatted(type, property.name(), property.type().defaultValue()));
        }

        // while(input.readTag())
        writer.println("        while(%s.readTag()) {".formatted(DEFAULT_STREAM_NAME));

        // switch(input.index())
        writer.println("            switch(%s.index()) {".formatted(DEFAULT_STREAM_NAME));
        var argumentsList = new ArrayList<String>();
        for(var property : message.properties()) {
            switch (property.type()) {
                case ProtobufPropertyType.MapType mapType -> writeMapSerializer(property, mapType);
                case ProtobufPropertyType.CollectionType collectionType -> writeDeserializer(property.name(), property.index(), collectionType.value(), true, property.packed());
                default -> writeDeserializer(property.name(), property.index(), property.type(), false, property.packed());
            }
            argumentsList.add(property.name());
        }
        writer.println("                default -> inputStream.skipBytes();");
        writer.println("            }");
        writer.println("        }");

        // Null check required properties
        message.properties()
                .stream()
                .filter(ProtobufPropertyElement::required)
                .forEach(this::checkRequiredProperty);

        // Return statement
        writer.println("        return new %s(%s);".formatted(message.element(), String.join(", ", argumentsList)));
    }

    private void writeMapSerializer(ProtobufPropertyElement property, ProtobufPropertyType.MapType mapType) {
        writer.println("                case %s -> {".formatted(property.index()));
        var streamName = "%sInputStream".formatted(property.name());
        var keyName = "%sKey".formatted(property.name());
        var valueName = "%sValue".formatted(property.name());
        writer.println("                        var %s = new ProtobufInputStream(%s.readBytes());".formatted(streamName, DEFAULT_STREAM_NAME));
        writer.println("                        %s %s = null;".formatted(mapType.keyType().implementationType(), keyName));
        writer.println("                        %s %s = null;".formatted(mapType.valueType().implementationType(), valueName));
        var keyReadMethod = getDeserializerStreamMethod(mapType.keyType(), false);
        var keyReadValue = getReadValue(streamName, mapType.keyType(), keyReadMethod);
        var keyReadFunction = getConvertedValue(mapType.keyType(), keyReadValue);
        var valueReadMethod = getDeserializerStreamMethod(mapType.valueType(), false);
        var valueReadValue = getReadValue(streamName, mapType.valueType(), valueReadMethod);
        var valueReadFunction = getConvertedValue(mapType.valueType(), valueReadValue);
        writer.println("                        while(%s.readTag()) {".formatted(streamName));
        writer.println("                            switch(%s.index()) {".formatted(streamName));
        writer.println("                                case 1 -> %s = %s;".formatted(keyName, keyReadFunction));
        writer.println("                                case 2 -> %s = %s;".formatted(valueName, valueReadFunction));
        writer.println("                            }");
        writer.println("                        }");
        writer.println("                        %s.put(%s, %s);".formatted(property.name(), keyName, valueName));
        writer.println("                }");
    }

    private void writeDeserializer(String name, int index, ProtobufPropertyType type, boolean repeated, boolean packed) {
        var readMethod = getDeserializerStreamMethod(type, packed);
        var readValue = getReadValue(DEFAULT_STREAM_NAME, type, readMethod);
        var readFunction = getConvertedValue(type, readValue);
        var readAssignment = getReadAssignment(name, repeated, packed, readFunction);
        writer.println("                case %s -> %s;".formatted(index, readAssignment));
    }

    private void checkRequiredProperty(ProtobufPropertyElement property) {
        if (!(property.type() instanceof ProtobufPropertyType.CollectionType)) {
            writer.println("        Objects.requireNonNull(%s, \"Missing required property: %s\");".formatted(property.name(), property.name()));
            return;
        }

        writer.println("        if(!%s.isEmpty())".formatted(property.name()));
        writer.println("            throw new NullPointerException(\"Missing required property: %s\");".formatted(property.name()));
    }

    private String getReadAssignment(String name, boolean repeated, boolean packed, String readFunction) {
        if (!repeated) {
            return "%s = %s".formatted(name, readFunction);
        }

        var repeatedMethod = packed ? "addAll" : "add";
        return "%s.%s(%s)".formatted(name, repeatedMethod, readFunction);
    }

    private String getReadValue(String streamName, ProtobufPropertyType type, String readMethod) {
        var reader = "%s.%s()".formatted(streamName, readMethod);
        if (type.protobufType() != ProtobufType.OBJECT) {
            return reader;
        }

        var specName = getSpecFromObject(type.implementationType());
        if(type.isEnum()) {
            return "%s.decode(%s).orElse(null)".formatted(specName, reader);
        }

        return "%s.decode(%s)".formatted(specName, reader);
    }

    private String getConvertedValue(ProtobufPropertyType implementation, String readValue) {
        var result = readValue;
        for(var converter : implementation.deserializers()) {
            if (converter.element().getKind() == ElementKind.CONSTRUCTOR) {
                var converterWrapperClass = (TypeElement) converter.element().getEnclosingElement();
                result = "new %s(%s)".formatted(converterWrapperClass.getQualifiedName(), result);
            } else {
                var converterWrapperClass = (TypeElement) converter.element().getEnclosingElement();
                var converterMethodName = converter.element().getSimpleName();
                result = "%s.%s(%s)".formatted(converterWrapperClass.getQualifiedName(), converterMethodName, result);
            }
        }
        return result;
    }

    // Returns the method to use to deserialize a property from ProtobufInputStream
    private String getDeserializerStreamMethod(ProtobufPropertyType type, boolean packed) {
        if (type.isEnum()) {
            return packed ? "readInt32Packed" : "readInt32";
        }
        
        return switch (type.protobufType()) {
            case STRING -> "readString";
            case OBJECT, BYTES -> "readBytes";
            case BOOL -> packed ? "readBoolPacked" : "readBool";
            case INT32, SINT32, UINT32 -> packed ? "readInt32Packed" : "readInt32";
            case FLOAT -> packed ? "readFloatPacked" : "readFloat";
            case DOUBLE -> packed ? "readDoublePacked" : "readDouble";
            case FIXED32, SFIXED32 -> packed ? "readFixed32Packed" : "readFixed32";
            case INT64, SINT64, UINT64 -> packed ? "readInt64Packed" : "readInt64";
            case FIXED64, SFIXED64 -> packed ? "readFixed64Packed" : "readFixed64";
            default -> throw new IllegalStateException("Unexpected value: " + type.protobufType());
        };
    }
}

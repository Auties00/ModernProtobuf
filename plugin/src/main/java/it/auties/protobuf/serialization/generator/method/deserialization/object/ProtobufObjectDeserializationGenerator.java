package it.auties.protobuf.serialization.generator.method.deserialization.object;

import it.auties.protobuf.exception.ProtobufDeserializationException;
import it.auties.protobuf.serialization.generator.method.deserialization.ProtobufDeserializationGenerator;
import it.auties.protobuf.serialization.model.object.ProtobufObjectElement;
import it.auties.protobuf.serialization.model.property.ProtobufPropertyElement;
import it.auties.protobuf.serialization.model.property.ProtobufPropertyType;
import it.auties.protobuf.serialization.support.JavaWriter.BodyWriter;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter.MethodWriter;
import it.auties.protobuf.serialization.support.JavaWriter.ClassWriter.SwitchStatementWriter;
import it.auties.protobuf.stream.ProtobufInputStream;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.List;

public class ProtobufObjectDeserializationGenerator extends ProtobufDeserializationGenerator {
    private static final String INPUT_STREAM_NAME = "protoInputStream";
    private static final String GROUP_INDEX_PARAMETER = "protoGroupIndex";
    private static final String ENUM_INDEX_PARAMETER = "protoEnumIndex";
    private static final String DEFAULT_UNKNOWN_FIELDS = "protoUnknownFields";
    private static final String FIELD_INDEX_VARIABLE = "protoFieldIndex";
    private static final String ENUM_DEFAULT_VALUE_PARAMETER = "defaultValue";
    public static final String ENUM_VALUES_FIELD = "VALUES";

    public ProtobufObjectDeserializationGenerator(ProtobufObjectElement element) {
        super(element);
    }

    @Override
    protected void doInstrumentation(ClassWriter classWriter, MethodWriter writer) {
        if (objectElement.type() == ProtobufObjectElement.Type.ENUM) {
            createEnumDeserializer(writer);
        }else {
            createMessageDeserializer(writer);
        }
    }

    @Override
    public boolean shouldInstrument() {
        return true;
    }

    @Override
    protected String returnType() {
        return objectElement.element().getSimpleName().toString();
    }

    @Override
    protected List<String> parametersTypes() {
        if(objectElement.type() == ProtobufObjectElement.Type.ENUM) {
            return List.of("int", objectElement.element().getSimpleName().toString());
        } else if(objectElement.type() == ProtobufObjectElement.Type.GROUP) {
            return List.of("int", ProtobufInputStream.class.getSimpleName());
        } else {
            return List.of(ProtobufInputStream.class.getSimpleName());
        }
    }

    @Override
    protected List<String> parametersNames() {
        if(objectElement.type() == ProtobufObjectElement.Type.ENUM) {
            return List.of(ENUM_INDEX_PARAMETER, ENUM_DEFAULT_VALUE_PARAMETER);
        } else if(objectElement.type() == ProtobufObjectElement.Type.GROUP) {
            return List.of(GROUP_INDEX_PARAMETER, INPUT_STREAM_NAME);
        } else {
            return List.of(INPUT_STREAM_NAME);
        }
    }

    private void checkPropertyIndex(BodyWriter writer, String indexField) {
        var conditions = new ArrayList<String>();
        for(var index : objectElement.reservedIndexes()) {
            switch (index) {
                case ProtobufObjectElement.ReservedIndex.Range range -> conditions.add("(%s >= %s && %s <= %s)".formatted(indexField, range.min(), indexField, range.max()));
                case ProtobufObjectElement.ReservedIndex.Value entry -> conditions.add("%s == %s".formatted(indexField, entry.value()));
            }
        }
        if(!conditions.isEmpty()) {
            try(var illegalIndexCheck = writer.printIfStatement(String.join(" || ", conditions))) {
                illegalIndexCheck.println("throw %s.reservedIndex(%s);".formatted(ProtobufDeserializationException.class.getName(), indexField));
            }
        }
    }

    private void createEnumDeserializer(MethodWriter writer) {
        checkPropertyIndex(writer, ENUM_INDEX_PARAMETER);
        writer.printReturn("%s.getOrDefault(%s, %s)".formatted(ENUM_VALUES_FIELD, ENUM_INDEX_PARAMETER, ENUM_DEFAULT_VALUE_PARAMETER));
    }

    private void createMessageDeserializer(MethodWriter methodWriter) {
        if(objectElement.type() == ProtobufObjectElement.Type.GROUP) {
            methodWriter.println("%s.assertGroupOpened(%s);".formatted(INPUT_STREAM_NAME, GROUP_INDEX_PARAMETER));
        }

        // Declare all variables
        // [<implementationType> var<index> = <defaultValue>, ...]
        for(var property : objectElement.properties()) {
            if(property.synthetic()) {
                continue;
            }

            var propertyType = property.type().descriptorElementType().toString();
            var propertyName = property.name();
            var propertyDefaultValue = property.type().descriptorDefaultValue();
            methodWriter.printVariableDeclaration(propertyType, propertyName, propertyDefaultValue);
        }

        // Declare the unknown fields valueType if needed
        objectElement.unknownFieldsElement()
                .ifPresent(unknownFieldsElement -> methodWriter.printVariableDeclaration(unknownFieldsElement.type().toString(), DEFAULT_UNKNOWN_FIELDS, unknownFieldsElement.defaultValue()));

        // Write deserializer implementation
        var argumentsList = new ArrayList<String>();
        try(var whileWriter = methodWriter.printWhileStatement(INPUT_STREAM_NAME + ".readTag()")) {
            whileWriter.printVariableDeclaration(FIELD_INDEX_VARIABLE, INPUT_STREAM_NAME + ".index()");
            checkPropertyIndex(whileWriter, FIELD_INDEX_VARIABLE);
            try(var switchWriter = whileWriter.printSwitchStatement(FIELD_INDEX_VARIABLE)) {
                for(var property : objectElement.properties()) {
                    if(property.synthetic()) {
                        continue;
                    }

                    switch (property.type()) {
                        case ProtobufPropertyType.MapType mapType -> writeMapDeserializer(switchWriter, property.index(), property.name(), mapType);
                        case ProtobufPropertyType.CollectionType collectionType -> writeDeserializer(switchWriter, property.name(), property.index(), collectionType.valueType(), true, property.packed(), null);
                        default -> writeDeserializer(switchWriter, property.name(), property.index(), property.type(), false, property.packed(), null);
                    }
                    argumentsList.add(property.name());
                }
                writeDefaultPropertyDeserializer(switchWriter);
            }
        }

        if(objectElement.type() == ProtobufObjectElement.Type.GROUP) {
            methodWriter.println("%s.assertGroupClosed(%s);".formatted(INPUT_STREAM_NAME, GROUP_INDEX_PARAMETER));
        }

        // Null check required properties
        objectElement.properties()
                .stream()
                .filter(ProtobufPropertyElement::required)
                .forEach(entry -> checkRequiredProperty(methodWriter, entry));

        // Return statement
        var unknownFieldsArg = objectElement.unknownFieldsElement().isEmpty() ? "" : ", " + DEFAULT_UNKNOWN_FIELDS;
        if(objectElement.deserializer().isPresent()) {
            methodWriter.printReturn("%s.%s(%s%s)".formatted(objectElement.element().getQualifiedName(), objectElement.deserializer().get().getSimpleName(), String.join(", ", argumentsList), unknownFieldsArg));
        }else {
            methodWriter.printReturn("new %s(%s%s)".formatted(objectElement.element().getQualifiedName(), String.join(", ", argumentsList), unknownFieldsArg));
        }
    }

    private void writeDefaultPropertyDeserializer(SwitchStatementWriter switchWriter) {
        var unknownFieldsElement = objectElement.unknownFieldsElement()
                .orElse(null);
        if(unknownFieldsElement == null) {
            switchWriter.printSwitchBranch("default", "%s.readUnknown(false)".formatted(INPUT_STREAM_NAME));
            return;
        }

        var setter = unknownFieldsElement.setter();
        var value = "%s.readUnknown(true)".formatted(INPUT_STREAM_NAME);
        if(setter.getModifiers().contains(Modifier.STATIC)) {
            var setterWrapperClass = (TypeElement) setter.getEnclosingElement();
            switchWriter.printSwitchBranch("default", "%s.%s(%s, %s, %s)".formatted(setterWrapperClass.getQualifiedName(), setter.getSimpleName(), DEFAULT_UNKNOWN_FIELDS, FIELD_INDEX_VARIABLE, value));
        }else {
            switchWriter.printSwitchBranch("default", "%s.%s(%s, %s)".formatted(DEFAULT_UNKNOWN_FIELDS, setter.getSimpleName(), FIELD_INDEX_VARIABLE, value));
        }
    }

    private void checkRequiredProperty(MethodWriter writer, ProtobufPropertyElement property) {
        if (!(property.type() instanceof ProtobufPropertyType.CollectionType)) {
            writer.println("Objects.requireNonNull(%s, \"Missing required property: %s\");".formatted(property.name(), property.name()));
            return;
        }

        try(var ifWriter = writer.printIfStatement("!%s.isEmpty()".formatted(property.name()))) {
            ifWriter.println("throw new NullPointerException(\"Missing required property: %s\");".formatted(property.name()));
        }
    }
}

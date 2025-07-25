package it.auties.protobuf.serialization.generator.method;

import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.serialization.model.ProtobufConverterElement;
import it.auties.protobuf.serialization.model.ProtobufPropertyType;
import it.auties.protobuf.serialization.model.ProtobufObjectElement;
import it.auties.protobuf.serialization.writer.BodyWriter;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class ProtobufSerializationGenerator extends ProtobufMethodGenerator {
    public static final String METHOD_NAME = "encode";
    private static final String OUTPUT_OBJECT_PARAMETER = "protoOutputStream";

    public ProtobufSerializationGenerator(ProtobufObjectElement element) {
        super(element);
    }

    protected void writeRepeatedSerializer(BodyWriter writer, int index, String name, String accessor, ProtobufPropertyType.CollectionType collectionType, boolean packed, boolean nullCheck, boolean cast) {
        if(packed) {
            var writeMethod = getStreamMethodName(collectionType.valueType().protobufType(), true);
            writer.println("%s.%s(%s, %s);".formatted(OUTPUT_OBJECT_PARAMETER, writeMethod.orElseThrow(), index, accessor));
        }else {
            var bodyWriter = nullCheck ? writer.printIfStatement("%s != null".formatted(accessor)) : writer;
            var localVariableName = "%sEntry".formatted(name); // Prevent shadowing
            try(var forEachWriter = bodyWriter.printForEachStatement(localVariableName, accessor)) {
                writeNormalSerializer(forEachWriter, index, name, localVariableName, collectionType.valueType(), false, true, cast);
            }
            if(nullCheck) {
                bodyWriter.close();
            }
        }
    }

    protected void writeMapSerializer(BodyWriter writer, int index, String name, String accessor, ProtobufPropertyType.MapType mapType, boolean nullCheck, boolean cast) {
        var bodyWriter = nullCheck ? writer.printIfStatement("%s != null".formatted(accessor)) : writer;
        var localVariableName = "%sEntry".formatted(name); // Prevent shadowing
        try(var forWriter = bodyWriter.printForEachStatement(localVariableName, accessor + ".entrySet()")) {
            var methodName = ProtobufSizeGenerator.getMapPropertyMethodName(name);
            forWriter.println("%s.writeMessage(%s, %s(%s%s));".formatted(OUTPUT_OBJECT_PARAMETER, index, methodName, cast ? "(java.util.Map.Entry) " : "", localVariableName));
            writeNormalSerializer(
                    forWriter,
                    1,
                    name + "Key",
                    "%s.getKey()".formatted(localVariableName),
                    mapType.keyType(),
                    false,
                    false,
                    cast
            );
            writeNormalSerializer(
                    forWriter,
                    2,
                    name + "Value",
                    "%s.getValue()".formatted(localVariableName),
                    mapType.valueType(),
                    true,
                    true,
                    cast
            );
        }
        if(nullCheck) {
            bodyWriter.close();
        }
    }

    protected void writeNormalSerializer(BodyWriter writer, int index, String name, String value, ProtobufPropertyType type, boolean declareVariable, boolean variableNullCheck, boolean cast) {
        writeCustomSerializer(
                writer,
                index,
                name,
                value,
                type,
                declareVariable,
                variableNullCheck,
                cast,
                (nestedWriter, serializedName, serializerStatements) -> {
                    for(var serializerStatement : serializerStatements) {
                        nestedWriter.println(serializerStatement);
                    }
                },
                (nestedWriter, serializedName, serializerStatements) -> {
                    for(var serializerStatement : serializerStatements) {
                        nestedWriter.println(serializerStatement);
                    }
                }
        );
    }

    protected void writeCustomSerializer(BodyWriter writer, int index, String name, String value, ProtobufPropertyType type, boolean declareVariable, boolean variableNullCheck, boolean cast, CustomSerializerHandler objectWriter, CustomSerializerHandler streamWriter) {
        // Apply cast if necessary
        if(cast) {
            var castType = getQualifiedName(type.descriptorElementType());
            value = "((%s) %s)".formatted(castType, value);
        }

        // Declare a variable using the provided name and valueType if necessary, or treat the valueType parameter as a property name
        var propertyName = declareVariable ? writer.printVariableDeclaration(name, value) : value;

        // Get the stream method used to serialize the final result
        var writeMethod = getStreamMethodName(type.protobufType(), false);

        // Declare a list of writers to handle nested null checks
        var nestedWriters = new ArrayList<BodyWriter>();
        nestedWriters.add(writer);

        // Null check the initial variable if necessary
        if(variableNullCheck && !(type.accessorType() instanceof PrimitiveType)) {
            nestedWriters.add(writer.printIfStatement("%s != null".formatted(propertyName)));
        }

        // Iterate through the serializers
        var serializers = type.serializers();
        var object = isObject(type);
        for(var i = 0; i < serializers.size(); i++) {
            var serializer = serializers.get(i);

            // Get the result of applying the serializer to the current result
            var result = createSerializerInvocation(serializer, propertyName, index);

            // If this is the last serializer and we are dealing with an object/group, invoke the method
            var lastSerializer = i == serializers.size() - 1;
            if ((lastSerializer && writeMethod.isEmpty()) || serializer.returnType().getKind() == TypeKind.VOID) {
                var statements = new ArrayList<String>();
                if(type.protobufType() == ProtobufType.MESSAGE) {
                    statements.add(getMessageMethod(index, serializer, propertyName));
                }
                statements.add("%s;".formatted(result));
                objectWriter.handle(nestedWriters.getLast(), propertyName, statements);
                continue;
            }

            // Declare a variable containing this serialization round's result
            propertyName = name + i;
            nestedWriters.getLast().printVariableDeclaration(propertyName, result);

            // If this isn't the last serializer, and the result isn't a primitive, null check it
            if ((object && lastSerializer) || serializer.returnType() instanceof PrimitiveType) {
                continue;
            }

            var newWriter = nestedWriters.getLast().printIfStatement("%s != null".formatted(propertyName));
            nestedWriters.add(newWriter);
        }

        if(writeMethod.isPresent()) {
            var result = "%s.%s(%s, %s%s);".formatted(
                    OUTPUT_OBJECT_PARAMETER,
                    writeMethod.get(),
                    index,
                    cast ? "(%s) ".formatted(type.protobufType().deserializableType().getName()) : "",
                    propertyName
            );
            streamWriter.handle(nestedWriters.getLast(), propertyName, List.of(result));
        }

        for (var i = nestedWriters.size() - 1; i >= 1; i--) {
            var nestedWriter = nestedWriters.get(i);
            nestedWriter.close();
        }
    }

    private String getMessageMethod(int index, ProtobufConverterElement.Attributed.Serializer serializer, String propertyName) {
        return "%s.writeMessage(%s, %s.%s(%s));".formatted(
                OUTPUT_OBJECT_PARAMETER,
                index,
                serializer.delegate().ownerName(),
                ProtobufSizeGenerator.METHOD_NAME,
                propertyName
        );
    }

    private boolean isObject(ProtobufPropertyType type) {
        return type.protobufType() == ProtobufType.MESSAGE
                || type.protobufType() == ProtobufType.ENUM
                || type.protobufType() == ProtobufType.GROUP;
    }

    // Callback function interface used to handle a serialization write request
    protected interface CustomSerializerHandler {
        // Writer: the body writer currently being used
        // Value: The expression produced by the current serializer
        // Statements: The statements adapted from the valueType, can be called to execute the serializer
        void handle(BodyWriter writer, String value, List<String> statements);
    }

    // Creates the method invocation for a given serializer using a valueType argument
    // Serializers cannot be constructors, we can assume that because of PreliminaryChecks
    private String createSerializerInvocation(ProtobufConverterElement.Attributed.Serializer serializer, String value, int groupIndex) {
        // If the serializer isn't static, invoke the serializer method on the valueType instance with no parameters
        // We can assume that the valueType on which the method is called will not be a message, enum or group because of PreliminaryChecks
        // class Wrapper {
        //    @ProtobufSerializer
        //    public String toValue() {
        //        ...
        //    }
        // }
        if (!serializer.delegate().modifiers().contains(Modifier.STATIC)) {
            return "%s.%s()".formatted(value, serializer.delegate().name());
        }

        // If the serializer was declared in a mixin, access the type of the mixin
        // Casting TypeElement should be fine here because a method's parent must be a class-like or interface program element
        return switch (serializer.delegate().parameters().size()) {
            // If the method only takes a parameter this is a normal mixin serializer, so we invoke the static method using valueType as a parameter
            // @ProtobufMixin
            // class SomeMixin {
            //    @ProtobufSerializer
            //    public static String toValue(Wrapper wrapper) {
            //        ...
            //    }
            // }
            case 1 -> "%s.%s(%s)".formatted(
                   serializer.delegate().ownerName(),
                    serializer.delegate().name(),
                    value
            );

            // If the method takes two parameters, this is a special case
            // In fact the only serializers allowed to take two parameters are synthetic serializers defined in the Spec class for messages and enums
            // We can assume this because of PreliminaryChecks
            // public class MessageSpec {
            //    public static void encode(Message protoInputObject, ProtobufOutputStream protoOutputStream) {
            //        ...
            //    }
            // }
            case 2 -> "%s.%s(%s, %s)".formatted(
                    serializer.delegate().ownerName(),
                    serializer.delegate().name(),
                    value,
                    OUTPUT_OBJECT_PARAMETER
            );

            // If the method takes three parameters, this is a special case
            // In fact the only serializers allowed to take three parameters are synthetic serializers defined in the Spec class for groups
            // We can assume this because of PreliminaryChecks
            // public class GroupSpec {
            //     public static void encode(int protoGroupIndex, GroupRecord protoInputObject, ProtobufOutputStream protoOutputStream) {
            //        ...
            //     }
            // }
            case 3 -> "%s.%s(%s, %s, %s)".formatted(
                    serializer.delegate().ownerName(),
                    serializer.delegate().name(),
                    groupIndex,
                    value,
                    OUTPUT_OBJECT_PARAMETER
            );

            // This should never happen
            default -> throw new IllegalArgumentException(
                    "Unexpected number of arguments for serializer "
                            +  serializer.delegate().name()
                            + " in "
                            + serializer.delegate().ownerName()
            );
        };
    }

    // Returns the method to use to deserialize a property from ProtobufOutputStream
    // Messages and enums don't have a serialization method, instead they use synthetic serializers
    // Maps should not be passed to this method, assuming the correct logic of this class
    // Unknown types are not expected, as assured by PreliminaryChecks
    private Optional<String> getStreamMethodName(ProtobufType protobufType, boolean packed) {
        // If available, get the method defined in ProtobufOutputStream for the input type
        var result = switch (protobufType) {
            case STRING -> "writeString";
            case UNKNOWN -> throw new IllegalArgumentException("Internal bug: unknown types should not reach getSerializerStreamMethod");
            case ENUM, INT32, SINT32 -> "writeInt32";
            case MESSAGE, GROUP -> null;
            case BYTES -> "writeBytes";
            case BOOL -> "writeBool";
            case UINT32 -> "writeUInt32";
            case MAP -> throw new IllegalArgumentException("Internal bug: map types should not reach getSerializerStreamMethod");
            case FLOAT -> "writeFloat";
            case DOUBLE -> "writeDouble";
            case FIXED32, SFIXED32 -> "writeFixed32";
            case INT64, SINT64 -> "writeInt64";
            case UINT64 -> "writeUInt64";
            case FIXED64, SFIXED64 -> "writeFixed64";
        };

        // If packed, get the packed method variant
        if(result != null && packed) {
            return Optional.of(result + "Packed");
        }

        // Return the result
        return Optional.ofNullable(result);
    }

    @Override
    protected String name() {
        return METHOD_NAME;
    }
}

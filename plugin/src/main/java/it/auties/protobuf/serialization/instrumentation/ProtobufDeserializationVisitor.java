package it.auties.protobuf.serialization.instrumentation;

import it.auties.protobuf.Protobuf;
import it.auties.protobuf.exception.ProtobufDeserializationException;
import it.auties.protobuf.model.ProtobufVersion;
import it.auties.protobuf.serialization.model.ProtobufMessageElement;
import it.auties.protobuf.serialization.model.ProtobufProperty;
import it.auties.protobuf.stream.ProtobufInputStream;
import org.objectweb.asm.*;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static it.auties.protobuf.model.ProtobufWireType.*;

public class ProtobufDeserializationVisitor extends ProtobufInstrumentationVisitor {
    public ProtobufDeserializationVisitor(ProtobufMessageElement element, ClassWriter classWriter) {
        super(element, classWriter);
    }

    @Override
    protected void doInstrumentation() {
        if (element.isEnum()) {
            createEnumDeserializer();
        }else {
            createMessageDeserializer();
        }
    }
    
    @Override
    protected int access() {
        return Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC;
    }
    
    @Override
    protected String name() {
        return element.isEnum() ? Protobuf.DESERIALIZATION_ENUM_METHOD
                : Protobuf.DESERIALIZATION_CLASS_METHOD;
    }
    
    @Override
    protected String descriptor() {
        if (element.isEnum()) {
            return "(I)Ljava/util/Optional;";
        }

        var versionType = Type.getType(ProtobufVersion.class);
        return "(L%s;[B)L%s;".formatted(versionType.getInternalName(), element.className());
    }
    
    @Override
    protected String signature() {
        if (!element.isEnum()) {
            return null;
        }

        return "(I)Ljava/util/Optional<L%s;>;".formatted(element.className());
    }

    @Override
    protected int argsCount() {
        return 2;
    }

    // Creates the body of the deserializer for a message
    private void createMessageDeserializer() {
        var inputStreamLocalId = createInputStream();
        var properties = createdLocalDeserializedVariables();
        var rawTagId = createLocalVariable();
        var tagId = createLocalVariable();
        createWhileStatement(
                Opcodes.IFNE,
                () -> createTagAndIndexReader(inputStreamLocalId, rawTagId, tagId),
                (whileOuterLabel) -> createPropertyDeserializer(whileOuterLabel, properties, inputStreamLocalId, rawTagId, tagId),
                () -> createReturnDeserializedValue(properties)
        );
    }

    // Creates a deserializer for any kind of property
    private void createPropertyDeserializer(Label whileOuterLabel, Map<ProtobufProperty, Integer> properties, int inputStreamLocalId, int rawTagId, int tagId) {
        pushPropertyIndexToStack(rawTagId, tagId);
        pushPropertyWireTypeToStack(rawTagId);
        createSwitchStatement(
                getPropertiesIndexes(),
                index -> createKnownPropertyDeserializer(whileOuterLabel, properties, inputStreamLocalId, tagId, index),
                () -> createUnknownPropertyDeserializer(whileOuterLabel)
        );
    }

    // Creates a deserializer for a property that is in the model
    private void createKnownPropertyDeserializer(Label whileOuterLabel, Map<ProtobufProperty, Integer> properties, int inputStreamLocalId, int tagId, int index) {
        var property = element.properties().get(index);
        switch (property.protoType()) {
            case MESSAGE -> createPropertyDeserializer(
                    property,
                    inputStreamLocalId,
                    tagId,
                    properties.get(property),
                    null,
                    () -> deserializeMessageFromBytes(property)
            );

            case ENUM -> createPropertyDeserializer(
                    property,
                    inputStreamLocalId,
                    tagId,
                    properties.get(property),
                    null,
                    () -> deserializeEnumFromInt(property)
            );

            case STRING -> createPropertyDeserializer(
                    property,
                    inputStreamLocalId,
                    tagId,
                    properties.get(property),
                    this::allocateNewString,
                    this::deserializeStringFromBytes
            );

            case BYTES -> createPropertyDeserializer(
                    property,
                    inputStreamLocalId,
                    tagId,
                    properties.get(property),
                    null,
                    null
            );

            default -> createPackablePropertyDeserializer(
                    property,
                    inputStreamLocalId,
                    tagId,
                    properties.get(property)
            );
        }

        methodVisitor.visitJumpInsn(
                Opcodes.GOTO,
                whileOuterLabel
        );
    }

    // Creates an instruction to break out of the switch statement as the property isn't known
    // According to the Protobuf spec, unknown properties shouldn't throw an error
    private void createUnknownPropertyDeserializer(Label whileOuterLabel) {
        methodVisitor.visitJumpInsn(
                Opcodes.GOTO,
                whileOuterLabel
        );
    }

    // Returns an array of all known indexes
    private int[] getPropertiesIndexes() {
        return element.properties()
                .stream()
                .mapToInt(ProtobufProperty::index)
                .toArray();
    }

    // Pushes the property's wire type to the stack
    // tag >> 3
    private void pushPropertyWireTypeToStack(int rawTagId) {
        methodVisitor.visitVarInsn(
                Opcodes.ILOAD,
                rawTagId
        );
        methodVisitor.visitInsn(
                Opcodes.ICONST_3
        );
        methodVisitor.visitInsn(
                Opcodes.IUSHR
        );
    }

    // Pushes the property's index to the stack
    // tag & 7
    private void pushPropertyIndexToStack(int rawTagId, int tagId) {
        methodVisitor.visitVarInsn(
                Opcodes.ILOAD,
                rawTagId
        );
        methodVisitor.visitIntInsn(
                Opcodes.BIPUSH,
                7
        );
        methodVisitor.visitInsn(
                Opcodes.IAND
        );
        methodVisitor.visitVarInsn(
                Opcodes.ISTORE,
                tagId
        );
    }

    // Returns the deserialized value by calling the message's constructor
    // it also checks that all required fields are filled
    private void createReturnDeserializedValue(LinkedHashMap<ProtobufProperty, Integer> properties) {
        properties.entrySet()
                .stream()
                .filter(entry -> entry.getKey().required())
                .forEach(entry -> applyNullCheck(entry.getKey(), entry.getValue()));
        methodVisitor.visitTypeInsn(
                Opcodes.NEW,
                element.className()
        );
        methodVisitor.visitInsn(
                Opcodes.DUP
        );
        var constructorArgsDescriptor = properties.entrySet()
                .stream()
                .peek(entry -> methodVisitor.visitVarInsn(Opcodes.ALOAD, entry.getValue()))
                .map(entry -> entry.getKey().javaType().getDescriptor())
                .collect(Collectors.joining(""));
        methodVisitor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                element.className(),
                "<init>",
                "(%s)V".formatted(constructorArgsDescriptor),
                false
        );
        methodVisitor.visitInsn(
                Opcodes.ARETURN
        );
    }

    // Reads the wire tag and index of the next field from the input stream
    private void createTagAndIndexReader(int inputStreamLocalId, int rawTagId, int tagId) {
        try {
            methodVisitor.visitVarInsn(
                    Opcodes.ALOAD,
                    inputStreamLocalId
            );
            methodVisitor.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    Type.getType(ProtobufInputStream.class).getInternalName(),
                    "readTag",
                    Type.getMethodDescriptor(ProtobufInputStream.class.getMethod("readTag")),
                    false
            );
            methodVisitor.visitInsn(
                    Opcodes.DUP
            );
            methodVisitor.visitVarInsn(
                    Opcodes.ISTORE,
                    rawTagId
            );
            methodVisitor.visitInsn(
                    Opcodes.DUP
            );
            methodVisitor.visitVarInsn(
                    Opcodes.ISTORE,
                    tagId
            );
        }catch (NoSuchMethodException exception) {
            throw new RuntimeException("Cannot create tag and index reader", exception);
        }
    }

    // Creates a ProtobufInputStream from the first and second parameters of the method(ProtobufVersion, byte[])
    private int createInputStream() {
        try {
            var inputStreamType = Type.getType(ProtobufInputStream.class);
            methodVisitor.visitTypeInsn(
                    Opcodes.NEW,
                    inputStreamType.getInternalName()
            );
            methodVisitor.visitInsn(
                    Opcodes.DUP
            );
            methodVisitor.visitVarInsn(
                    Opcodes.ALOAD,
                    0
            );
            methodVisitor.visitVarInsn(
                    Opcodes.ALOAD,
                    1
            );
            methodVisitor.visitMethodInsn(
                    Opcodes.INVOKESPECIAL,
                    inputStreamType.getInternalName(),
                    "<init>",
                    Type.getConstructorDescriptor(ProtobufInputStream.class.getConstructor(ProtobufVersion.class, byte[].class)),
                    false
            );
            methodVisitor.visitVarInsn(
                    Opcodes.ASTORE,
                    createLocalVariable()
            );
            return localsCount;
        }catch (NoSuchMethodException throwable) {
            throw new RuntimeException("Cannot create input stream var", throwable);
        }
    }

    // Allocates a new String to prepare for deserialization
    private void allocateNewString() {
        var stringType = Type.getType(String.class);
        methodVisitor.visitTypeInsn(
                Opcodes.NEW,
                stringType.getInternalName()
        );
        methodVisitor.visitInsn(
                Opcodes.DUP
        );
    }

    // Deserializes a String from an array of bytes
    private void deserializeStringFromBytes() {
        try {
            methodVisitor.visitFieldInsn(
                    Opcodes.GETSTATIC,
                    Type.getType(StandardCharsets.class).getInternalName(),
                    "UTF_8",
                    Type.getType(String.class).getDescriptor()
            );
            methodVisitor.visitMethodInsn(
                    Opcodes.INVOKESPECIAL,
                    element.className(),
                    "<init>",
                    Type.getConstructorDescriptor(String.class.getConstructor(byte[].class, Charset.class)),
                    false
            );
        }catch (NoSuchMethodException exception) {
            throw new RuntimeException("Cannot create deserializer bytes -> string", exception);
        }
    }

    // Deserializes an Enum or null object from an index
    private void deserializeEnumFromInt(ProtobufProperty property) {
        methodVisitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                property.javaType().getInternalName(),
                Protobuf.DESERIALIZATION_ENUM_METHOD,
                "(I)Ljava/lang/Optional;",
                false
        );
        methodVisitor.visitInsn(
                Opcodes.ACONST_NULL
        );
        methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                Type.getType(Optional.class).getInternalName(),
                "orElse",
                "(Ljava/lang/Object;)L%s;".formatted(element.className()),
                false
        );
    }

    // Deserializes a message from bytes
    private void deserializeMessageFromBytes(ProtobufProperty property) {
        methodVisitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                property.javaType().getInternalName(),
                Protobuf.DESERIALIZATION_CLASS_METHOD,
                "([B)L%s;".formatted(property.javaType().getInternalName()),
                false
        );
    }

    // Invokes Objects.requireNonNull on a required field
    private void applyNullCheck(ProtobufProperty property, int localVariableId) {
        try {
            methodVisitor.visitVarInsn(
                    Opcodes.ALOAD,
                    localVariableId
            );
            methodVisitor.visitLdcInsn(
                    "Missing required field: " + property.name()
            );
            methodVisitor.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    Type.getType(Objects.class).getInternalName(),
                    "requireNonNull",
                    Type.getMethodDescriptor(Objects.class.getMethod("requireNonNull", Object.class, String.class)),
                    false
            );
            methodVisitor.visitInsn(
                    Opcodes.POP
            );
        } catch (NoSuchMethodException throwable) {
            throw new RuntimeException("Cannot write message null check", throwable);
        }
    }

    // Creates a Map that links every property to a local variable that holds its deserialized value
    // A LinkedHashMap is used to preserve the order of the properties
    private LinkedHashMap<ProtobufProperty, Integer> createdLocalDeserializedVariables() {
        return element.properties()
                .stream()
                .map(entry -> Map.entry(entry, createLocalDeserializeVariable(entry)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first, LinkedHashMap::new));
    }

    // Creates a local variable to store a property's deserialized value
    private int createLocalDeserializeVariable(ProtobufProperty property) {
        addDeserializerDefaultValue(property);
        methodVisitor.visitVarInsn(
                Opcodes.ASTORE,
                createLocalVariable()
        );
        return localsCount;
    }

    // Pushes to the stack the correct default value for the local variable created by createDeserializedField
    private void addDeserializerDefaultValue(ProtobufProperty property) {
        if(property.wrapperType() != null){
            methodVisitor.visitTypeInsn(
                    Opcodes.NEW,
                    property.wrapperType().getInternalName()
            );
            methodVisitor.visitInsn(
                    Opcodes.DUP
            );
            methodVisitor.visitMethodInsn(
                    Opcodes.INVOKESPECIAL,
                    property.wrapperType().getInternalName(),
                    "<init>",
                    "()V",
                    false
            );
            return;
        }

        var sort = property.javaType().getSort();
        switch (sort) {
            case Type.OBJECT, Type.ARRAY ->  methodVisitor.visitInsn(Opcodes.ACONST_NULL);
            case Type.INT, Type.BOOLEAN, Type.CHAR, Type.SHORT, Type.BYTE -> methodVisitor.visitInsn(Opcodes.ICONST_0);
            case Type.FLOAT -> methodVisitor.visitInsn(Opcodes.FCONST_0);
            case Type.DOUBLE -> methodVisitor.visitInsn(Opcodes.DCONST_0);
            case Type.LONG -> methodVisitor.visitInsn(Opcodes.LCONST_0);
            default -> throw new RuntimeException("Unexpected type: " + property.javaType().getClassName());
        }
    }

    // Creates a deserializer for a packable property
    private void createPackablePropertyDeserializer(ProtobufProperty property, int inputStreamId, int tagId, int fieldId) {
        if(!property.packed()) {
            methodVisitor.visitVarInsn(
                    Opcodes.ILOAD,
                    tagId
            );
            pushIntToStack(
                    getWireType(property)
            );
            createIfStatement(
                    methodVisitor,
                    Opcodes.IF_ICMPEQ,
                    () -> createSafePackedPropertyDeserializer(
                            
                            property,
                            inputStreamId,
                            fieldId,
                            false
                    ),
                    () -> throwInvalidTag(tagId)
            );
            return;
        }

        createSwitchStatement(
                new int[]{WIRE_TYPE_LENGTH_DELIMITED, WIRE_TYPE_VAR_INT},
                index -> createPackedPropertyDeserializer(property, inputStreamId, fieldId, index),
                () -> throwInvalidTag(tagId)
        );
    }

    // Creates a deserializer for a packed property
    private void createPackedPropertyDeserializer(ProtobufProperty property, int inputStreamId, int fieldId, int index) {
        switch (index) {
            case WIRE_TYPE_LENGTH_DELIMITED -> createSafePackedPropertyDeserializer(
                    property,
                    inputStreamId,
                    fieldId,
                    true
            );
            case WIRE_TYPE_VAR_INT -> createSafePackedPropertyDeserializer(
                    property,
                    inputStreamId,
                    fieldId,
                    false
            );
            default -> throw new IllegalArgumentException("Unknown index: " + index);
        }
    }

    // Creates a deserializer for a packed property assuming that the wire type is correct
    private void createSafePackedPropertyDeserializer(ProtobufProperty property, int inputStreamId, int fieldId, boolean packedWireType) {
        try {
            var repeated = property.repeated();
            if(repeated) {
                methodVisitor.visitVarInsn(
                        Opcodes.ALOAD,
                        fieldId
                );
            }
            methodVisitor.visitVarInsn(
                    Opcodes.ALOAD,
                    inputStreamId
            );
            var method = getDeserializerStreamMethod(property);
            methodVisitor.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    Type.getType(ProtobufInputStream.class).getInternalName(),
                    method,
                    Type.getMethodDescriptor(ProtobufInputStream.class.getMethod(method)),
                    false
            );
            if (property.repeated()) {
                methodVisitor.visitMethodInsn(
                        Opcodes.INVOKEVIRTUAL,
                        packedWireType ? property.wrapperType().getInternalName() : property.javaType().getInternalName(),
                        packedWireType ? "addAll" : "add",
                        Type.getMethodDescriptor(packedWireType ? Collection.class.getMethod("addAll", Collection.class) : Collection.class.getMethod("add", Object.class)),
                        false
                );
            } else {
                methodVisitor.visitVarInsn(
                        Opcodes.ASTORE,
                        fieldId
                );
            }
        }catch (NoSuchMethodException exception) {
            throw new RuntimeException("Cannot create packed deserializer", exception);
        }
    }

    // Returns the method to use to deserialize a property from ProtobufInputStream
    private String getDeserializerStreamMethod(ProtobufProperty annotation) {
        return switch (annotation.protoType()) {
            case MESSAGE, BYTES, STRING -> {
                if (annotation.packed()) {
                    throw new IllegalArgumentException("%s %s is packed: only scalar types are allowed to have this modifier".formatted(annotation.protoType().name(), annotation.name()));
                }

                yield "readBytes";
            }
            case ENUM, INT32, SINT32, UINT32, BOOL -> annotation.packed() ? "readInt32Packed" : "readInt32";
            case FLOAT -> annotation.packed() ? "readFloatPacked" : "readFloat";
            case DOUBLE -> annotation.packed() ? "readDoublePacked" : "readDouble";
            case FIXED32, SFIXED32 -> annotation.packed() ? "readFixed32Packed" : "readFixed32";
            case INT64, SINT64, UINT64 -> annotation.packed() ? "readInt64Packed" :  "readInt64";
            case FIXED64, SFIXED64 -> annotation.packed() ? "readFixed64Packed" : "readFixed64";
        };
    }

    // Creates a message deserializer with a wire tag check
    private void createPropertyDeserializer(ProtobufProperty property, int inputStreamId, int tagId, int fieldId, Runnable preparer, Runnable finalizer) {
        methodVisitor.visitVarInsn(
                Opcodes.ILOAD,
                tagId
        );
        pushIntToStack(
                getWireType(property)
        );
        createIfStatement(
                methodVisitor,
                Opcodes.IF_ICMPEQ,
                () -> createSafePropertyDeserializer(
                        property,
                        inputStreamId,
                        fieldId,
                        preparer,
                        finalizer
                ),
                () -> throwInvalidTag(tagId)
        );
    }

    // Returns the wire-type of a property
    private int getWireType(ProtobufProperty annotation) {
        return switch (annotation.protoType()) {
            case MESSAGE, STRING, BYTES -> WIRE_TYPE_LENGTH_DELIMITED;
            case ENUM, BOOL, INT32, SINT32, UINT32, INT64, SINT64, UINT64 -> WIRE_TYPE_VAR_INT;
            case FLOAT, FIXED32, SFIXED32 -> WIRE_TYPE_FIXED32;
            case DOUBLE, FIXED64, SFIXED64 -> WIRE_TYPE_FIXED64;
        };
    }

    // Creates a message deserializer assuming that the wire tag is correct
    private void createSafePropertyDeserializer(ProtobufProperty property, int inputStreamId, int fieldId, Runnable preparer, Runnable finalizer) {
        try {
            var repeated = property.repeated();
            if(repeated) {
                methodVisitor.visitVarInsn(
                        Opcodes.ALOAD,
                        fieldId
                );
            }
            if(preparer != null) {
                preparer.run();
            }
            methodVisitor.visitVarInsn(
                    Opcodes.ALOAD,
                    inputStreamId
            );
            var method = getDeserializerStreamMethod(property);
            methodVisitor.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    Type.getType(ProtobufInputStream.class).getInternalName(),
                    method,
                    Type.getMethodDescriptor(ProtobufInputStream.class.getMethod(method)),
                    false
            );
            if(finalizer != null) {
                finalizer.run();
            }
            if (repeated) {
                methodVisitor.visitMethodInsn(
                        Opcodes.INVOKEVIRTUAL,
                        property.wrapperType().getInternalName(),
                        "add",
                        Type.getMethodDescriptor(Collection.class.getMethod("add", Object.class)),
                        false
                );
            } else {
                methodVisitor.visitVarInsn(
                        Opcodes.ASTORE,
                        fieldId
                );
            }
        }catch (NoSuchMethodException exception) {
            throw new RuntimeException("Cannot create message deserializer", exception);
        }
    }

    // Throws a ProtobufDeserializationException.invalidTag exception with tagId as a parameter
    private void throwInvalidTag(int tagId) {
        try {
            methodVisitor.visitVarInsn(
                    Opcodes.ILOAD,
                    tagId
            );
            methodVisitor.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    Type.getType(ProtobufDeserializationException.class).getInternalName(),
                    "invalidTag",
                    Type.getMethodDescriptor(ProtobufDeserializationException.class.getMethod("invalidTag", int.class)),
                    false
            );
            methodVisitor.visitInsn(
                    Opcodes.ATHROW
            );
        }catch (NoSuchMethodException exception) {
            throw new RuntimeException("Cannot write invalid tag", exception);
        }
    }

    // Creates the body of the deserializer for an Enum
    // It looks like this:
    // Optional<Type> of(int index) = Arrays.stream(values()).filter(entry -> entry.index == index).findFirst();
    private void createEnumDeserializer() {
        try {
            methodVisitor.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    element.className(),
                    "values",
                    "()[L%s;".formatted(element.className()),
                    false
            );
            methodVisitor.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    Type.getType(Arrays.class).getInternalName(),
                    "stream",
                    Type.getMethodDescriptor(Arrays.class.getMethod("stream", Object[].class)),
                    false
            );
            var lambdaFactoryInvocationParams = createEnumConstructorLambda();
            methodVisitor.visitVarInsn(
                    Opcodes.ILOAD,
                    0
            );
            methodVisitor.visitInvokeDynamicInsn(
                    "test",
                    "(I)Z",
                    new Handle(
                            Opcodes.H_INVOKESTATIC,
                            Type.getType(LambdaMetafactory.class).getInternalName(),
                            "metafactory",
                            Type.getMethodDescriptor(LambdaMetafactory.class.getMethod("metafactory", MethodHandles.Lookup.class, String.class, MethodType.class, MethodType.class, MethodHandle.class, MethodType.class)),
                            false
                    ),
                    lambdaFactoryInvocationParams
            );
            methodVisitor.visitMethodInsn(
                    Opcodes.INVOKEINTERFACE,
                    Type.getType(Stream.class).getInternalName(),
                    "filter",
                    Type.getMethodDescriptor(Stream.class.getMethod("filter", Predicate.class)),
                    true
            );
            methodVisitor.visitMethodInsn(
                    Opcodes.INVOKEINTERFACE,
                    Type.getType(Stream.class).getInternalName(),
                    "findFirst",
                    Type.getMethodDescriptor(Stream.class.getMethod("findFirst")),
                    true
            );
            methodVisitor.visitInsn(
                    Opcodes.ARETURN
            );
        } catch (NoSuchMethodException throwable) {
            throw new RuntimeException("Cannot instrument enum", throwable);
        }
    }

    // Creates a lambda that looks like this:
    // (ProtobufEnum entry) -> entry.index == index
    private Object[] createEnumConstructorLambda() {
        var lambdaName = "lambda$of$0";
        var lambdaDescriptor = "(IL%s;)Z".formatted(element.className());
        var predicateLambdaMethod = classWriter.visitMethod(
                Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC + Opcodes.ACC_SYNTHETIC,
                lambdaName,
                lambdaDescriptor,
                null,
                new String[0]
        );
        predicateLambdaMethod.visitCode();
        predicateLambdaMethod.visitVarInsn(
                Opcodes.ALOAD,
                1
        );
        predicateLambdaMethod.visitFieldInsn(
                Opcodes.GETFIELD,
                element.className(),
                "index",
                "I"
        );
        predicateLambdaMethod.visitVarInsn(
                Opcodes.ILOAD,
                0
        );
        createIfStatement(
                predicateLambdaMethod,
                Opcodes.IF_ICMPNE,
                () -> {
                    predicateLambdaMethod.visitInsn(Opcodes.ICONST_0);
                    predicateLambdaMethod.visitInsn(Opcodes.IRETURN);
                },
                () -> {
                    predicateLambdaMethod.visitInsn(Opcodes.ICONST_1);
                    predicateLambdaMethod.visitInsn(Opcodes.IRETURN);
                }
        );
        predicateLambdaMethod.visitMaxs(0,0);
        predicateLambdaMethod.visitEnd();
        return new Object[]{
                Type.getType("(Ljava/lang/Object;)Z"),
                new Handle(
                        Opcodes.H_INVOKESTATIC,
                        element.className(),
                        lambdaName,
                        lambdaDescriptor,
                        false
                ),
                Type.getType("(I)Z")
        };
    }

    @SuppressWarnings("SameParameterValue")
    private void createWhileStatement(int operator, Runnable preparer, Consumer<Label> whileBranch, Runnable outerBranch) {
        var whileOuterLabel = new Label();
        methodVisitor.visitLabel(whileOuterLabel);
        preparer.run();
        var whileInnerLabel = new Label();
        methodVisitor.visitJumpInsn(operator, whileInnerLabel);
        outerBranch.run();
        methodVisitor.visitLabel(whileInnerLabel);
        whileBranch.accept(whileOuterLabel);
        methodVisitor.visitJumpInsn(Opcodes.GOTO, whileOuterLabel);
    }

    // Creates an if statement
    private void createIfStatement(MethodVisitor visitor, int instruction, Runnable trueBranch, Runnable falseBranch) {
        var trueBranchLabel = new Label();
        visitor.visitJumpInsn(instruction, trueBranchLabel);
        falseBranch.run();
        visitor.visitLabel(trueBranchLabel);
        trueBranch.run();
    }

    // Creates a switch statement
    private void createSwitchStatement(int[] knownBranches, IntConsumer knownBranch, Runnable defaultBranch) {
        var unknownPropertyLabel = new Label();
        var labels = IntStream.range(0, knownBranches.length)
                .mapToObj(ignored -> new Label())
                .toArray(Label[]::new);
        methodVisitor.visitLookupSwitchInsn(
                unknownPropertyLabel,
                knownBranches,
                labels
        );
        methodVisitor.visitLabel(unknownPropertyLabel);
        defaultBranch.run();
        for(var index = 0; index < labels.length; index++) {
            var propertyLabel = labels[index];
            methodVisitor.visitLabel(propertyLabel);
            knownBranch.accept(index);
        }
    }
}

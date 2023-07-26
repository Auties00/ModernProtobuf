package it.auties.protobuf.serialization;

import it.auties.protobuf.Protobuf;
import it.auties.protobuf.exception.ProtobufDeserializationException;
import it.auties.protobuf.model.ProtobufVersion;
import it.auties.protobuf.stream.ProtobufInputStream;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.LocalVariablesSorter;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static it.auties.protobuf.model.ProtobufWireType.*;

class ProtobufDeserializationVisitor extends ClassVisitor {
    private final ProtobufMessageElement element;

    protected ProtobufDeserializationVisitor(ProtobufMessageElement element, ClassVisitor classVisitor) {
        super(Opcodes.ASM9);
        this.element = element;
        this.cv = classVisitor;
    }

    @Override
    public void visitEnd() {
        var methodAccess = getMethodAccess();
        var methodName = getMethodName();
        var methodDescriptor = getMethodDescriptor();
        var methodSignature = getMethodSignature();
        var methodVisitor = cv.visitMethod(
                methodAccess,
                methodName,
                methodDescriptor,
                methodSignature,
                new String[0]
        );
        methodVisitor.visitCode();
        if (element.isEnum()) {
            createEnumDeserializer(methodVisitor);
        }else {
            createMessageDeserializer(methodVisitor, methodAccess, methodName, methodDescriptor);
        }
        methodVisitor.visitMaxs(-1, -1);
        methodVisitor.visitEnd();
        cv.visitEnd();
    }

    // Returns the modifiers for the deserializer
    private int getMethodAccess() {
        return Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC;
    }

    // Returns the name of the deserializer
    private String getMethodName() {
        return element.isEnum() ? Protobuf.DESERIALIZATION_ENUM_METHOD
                : Protobuf.DESERIALIZATION_CLASS_METHOD;
    }

    // Returns the descriptor of the deserializer
    private String getMethodDescriptor() {
        if (element.isEnum()) {
            return "(I)Ljava/util/Optional;";
        }

        var versionType = Type.getType(ProtobufVersion.class);
        return "(L%s;[B)L%s;".formatted(versionType.getInternalName(), element.className());
    }

    // Returns the signature of the deserializer
    private String getMethodSignature() {
        if (!element.isEnum()) {
            return null;
        }

        return "(I)Ljava/util/Optional<L%s;>;".formatted(element.className());
    }

    // Creates the body of the deserializer for a message
    private void createMessageDeserializer(MethodVisitor methodVisitor, int access, String methodName, String methodDescriptor) {
        var localCreator = new GeneratorAdapter(
                methodVisitor,
                access,
                methodName,
                methodDescriptor
        );
        var inputStreamLocalId = createInputStream(localCreator);
        var properties = createdLocalDeserializedVariables(localCreator);
        var rawTagId = localCreator.newLocal(Type.INT_TYPE);
        var tagId = localCreator.newLocal(Type.INT_TYPE);
        createWhileStatement(
                localCreator,
                Opcodes.IFNE,
                visitor -> createTagAndIndexReader(localCreator, inputStreamLocalId, rawTagId, tagId),
                (visitor, whileOuterLabel) -> createPropertyDeserializer(localCreator, whileOuterLabel, properties, inputStreamLocalId, rawTagId, tagId),
                visitor -> createReturnDeserializedValue(methodVisitor, localCreator, properties)
        );
    }

    // Creates a deserializer for any kind of property
    private void createPropertyDeserializer(MethodVisitor methodVisitor, Label whileOuterLabel, Map<ProtobufPropertyStub, Integer> properties, int inputStreamLocalId, int rawTagId, int tagId) {
        pushPropertyIndexToStack(methodVisitor, rawTagId, tagId);
        pushPropertyWireTypeToStack(methodVisitor, rawTagId);
        createSwitchStatement(
                methodVisitor,
                getPropertiesIndexes(),
                index -> createKnownPropertyDeserializer(methodVisitor, whileOuterLabel, properties, inputStreamLocalId, tagId, index),
                visitor -> createUnknownPropertyDeserializer(methodVisitor, whileOuterLabel)
        );
    }

    // Creates a deserializer for a property that is in the model
    private void createKnownPropertyDeserializer(MethodVisitor methodVisitor, Label whileOuterLabel, Map<ProtobufPropertyStub, Integer> properties, int inputStreamLocalId, int tagId, int index) {
        var property = element.properties().get(index);
        switch (property.protoType()) {
            case MESSAGE -> createPropertyDeserializer(
                    methodVisitor,
                    property,
                    inputStreamLocalId,
                    tagId,
                    properties.get(property),
                    null,
                    innerVisitor -> deserializeMessageFromBytes(innerVisitor, property)
            );

            case ENUM -> createPropertyDeserializer(
                    methodVisitor,
                    property,
                    inputStreamLocalId,
                    tagId,
                    properties.get(property),
                    null,
                    innerVisitor -> deserializeEnumFromInt(innerVisitor, property)
            );

            case STRING -> createPropertyDeserializer(
                    methodVisitor,
                    property,
                    inputStreamLocalId,
                    tagId,
                    properties.get(property),
                    this::allocateNewString,
                    this::deserializeStringFromBytes
            );

            case BYTES -> createPropertyDeserializer(
                    methodVisitor,
                    property,
                    inputStreamLocalId,
                    tagId,
                    properties.get(property),
                    null,
                    null
            );

            default -> createPackablePropertyDeserializer(
                    methodVisitor,
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
    private void createUnknownPropertyDeserializer(MethodVisitor methodVisitor, Label whileOuterLabel) {
        methodVisitor.visitJumpInsn(
                Opcodes.GOTO,
                whileOuterLabel
        );
    }

    // Returns an array of all known indexes
    private int[] getPropertiesIndexes() {
        return element.properties()
                .stream()
                .mapToInt(ProtobufPropertyStub::index)
                .toArray();
    }

    // Pushes the property's wire type to the stack
    // tag >> 3
    private void pushPropertyWireTypeToStack(MethodVisitor methodVisitor, int rawTagId) {
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
    private void pushPropertyIndexToStack(MethodVisitor methodVisitor, int rawTagId, int tagId) {
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
    private void createReturnDeserializedValue(MethodVisitor methodVisitor, GeneratorAdapter localCreator, LinkedHashMap<ProtobufPropertyStub, Integer> properties) {
        properties.entrySet()
                .stream()
                .filter(entry -> entry.getKey().required())
                .forEach(entry -> applyNullCheck(methodVisitor, entry.getKey(), entry.getValue()));
        localCreator.visitTypeInsn(
                Opcodes.NEW,
                element.className()
        );
        localCreator.visitInsn(
                Opcodes.DUP
        );
        var constructorArgsDescriptor = properties.entrySet()
                .stream()
                .peek(entry -> localCreator.visitVarInsn(Opcodes.ALOAD, entry.getValue()))
                .map(entry -> entry.getKey().javaType().getDescriptor())
                .collect(Collectors.joining(""));
        localCreator.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                element.className(),
                "<init>",
                "(%s)V".formatted(constructorArgsDescriptor),
                false
        );
        localCreator.visitInsn(
                Opcodes.ARETURN
        );
    }

    // Reads the wire tag and index of the next field from the input stream
    private void createTagAndIndexReader(GeneratorAdapter localCreator, int inputStreamLocalId, int rawTagId, int tagId) {
        try {
            localCreator.visitVarInsn(
                    Opcodes.ALOAD,
                    inputStreamLocalId
            );
            localCreator.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    Type.getType(ProtobufInputStream.class).getInternalName(),
                    "readTag",
                    Type.getMethodDescriptor(ProtobufInputStream.class.getMethod("readTag")),
                    false
            );
            localCreator.visitInsn(
                    Opcodes.DUP
            );
            localCreator.visitVarInsn(
                    Opcodes.ISTORE,
                    rawTagId
            );
            localCreator.visitInsn(
                    Opcodes.DUP
            );
            localCreator.visitVarInsn(
                    Opcodes.ISTORE,
                    tagId
            );
        }catch (NoSuchMethodException exception) {
            throw new RuntimeException("Cannot create tag and index reader", exception);
        }
    }

    // Creates a ProtobufInputStream from the first and second parameters of the method(ProtobufVersion, byte[])
    private int createInputStream(LocalVariablesSorter localCreator) {
        try {
            var inputStreamType = Type.getType(ProtobufInputStream.class);
            localCreator.visitTypeInsn(
                    Opcodes.NEW,
                    inputStreamType.getInternalName()
            );
            localCreator.visitInsn(
                    Opcodes.DUP
            );
            localCreator.visitVarInsn(
                    Opcodes.ALOAD,
                    0
            );
            localCreator.visitVarInsn(
                    Opcodes.ALOAD,
                    1
            );
            localCreator.visitMethodInsn(
                    Opcodes.INVOKESPECIAL,
                    inputStreamType.getInternalName(),
                    "<init>",
                    Type.getConstructorDescriptor(ProtobufInputStream.class.getConstructor(ProtobufVersion.class, byte[].class)),
                    false
            );
            var streamLocalId = localCreator.newLocal(inputStreamType);
            localCreator.visitVarInsn(
                    Opcodes.ASTORE,
                    streamLocalId
            );
            return streamLocalId;
        }catch (NoSuchMethodException throwable) {
            throw new RuntimeException("Cannot create input stream var", throwable);
        }
    }

    // Allocates a new String to prepare for deserialization
    private void allocateNewString(MethodVisitor visitor) {
        var stringType = Type.getType(String.class);
        visitor.visitTypeInsn(
                Opcodes.NEW,
                stringType.getInternalName()
        );
        visitor.visitInsn(
                Opcodes.DUP
        );
    }

    // Deserializes a String from an array of bytes
    private void deserializeStringFromBytes(MethodVisitor visitor) {
        try {
            visitor.visitFieldInsn(
                    Opcodes.GETSTATIC,
                    Type.getType(StandardCharsets.class).getInternalName(),
                    "UTF_8",
                    Type.getType(String.class).getDescriptor()
            );
            visitor.visitMethodInsn(
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
    private void deserializeEnumFromInt(MethodVisitor visitor, ProtobufPropertyStub property) {
        visitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                property.javaType().getInternalName(),
                Protobuf.DESERIALIZATION_ENUM_METHOD,
                "(I)Ljava/lang/Optional;",
                false
        );
        visitor.visitInsn(
                Opcodes.ACONST_NULL
        );
        visitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                Type.getType(Optional.class).getInternalName(),
                "orElse",
                "(Ljava/lang/Object;)L%s;".formatted(element.className()),
                false
        );
    }

    // Deserializes a message from bytes
    private void deserializeMessageFromBytes(MethodVisitor visitor, ProtobufPropertyStub property) {
        visitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                property.javaType().getInternalName(),
                Protobuf.DESERIALIZATION_CLASS_METHOD,
                "([B)L%s;".formatted(property.javaType().getInternalName()),
                false
        );
    }

    // Invokes Objects.requireNonNull on a required field
    private void applyNullCheck(MethodVisitor methodVisitor, ProtobufPropertyStub property, int localVariableId) {
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
    private LinkedHashMap<ProtobufPropertyStub, Integer> createdLocalDeserializedVariables(LocalVariablesSorter localCreator) {
        return element.properties()
                .stream()
                .map(entry -> Map.entry(entry, createLocalDeserializeVariable(localCreator, entry)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first, LinkedHashMap::new));
    }

    // Creates a local variable to store a property's deserialized value
    private int createLocalDeserializeVariable(LocalVariablesSorter localCreator, ProtobufPropertyStub property) {
        addDeserializerDefaultValue(localCreator, property);
        var variableId = localCreator.newLocal(property.javaType());
        localCreator.visitVarInsn(
                Opcodes.ASTORE,
                variableId
        );
        return variableId;
    }

    // Pushes to the stack the correct default value for the local variable created by createDeserializedField
    private void addDeserializerDefaultValue(LocalVariablesSorter localCreator, ProtobufPropertyStub property) {
        if(property.wrapperType() != null){
            localCreator.visitTypeInsn(
                    Opcodes.NEW,
                    property.wrapperType().getInternalName()
            );
            localCreator.visitInsn(
                    Opcodes.DUP
            );
            localCreator.visitMethodInsn(
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
            case Type.OBJECT, Type.ARRAY ->  localCreator.visitInsn(Opcodes.ACONST_NULL);
            case Type.INT, Type.BOOLEAN, Type.CHAR, Type.SHORT, Type.BYTE -> localCreator.visitInsn(Opcodes.ICONST_0);
            case Type.FLOAT -> localCreator.visitInsn(Opcodes.FCONST_0);
            case Type.DOUBLE -> localCreator.visitInsn(Opcodes.DCONST_0);
            case Type.LONG -> localCreator.visitInsn(Opcodes.LCONST_0);
            default -> throw new RuntimeException("Unexpected type: " + property.javaType().getClassName());
        }
    }

    // Creates a deserializer for a packable property
    private void createPackablePropertyDeserializer(MethodVisitor visitor, ProtobufPropertyStub property, int inputStreamId, int tagId, int fieldId) {
        if(!property.packed()) {
            visitor.visitVarInsn(
                    Opcodes.ILOAD,
                    tagId
            );
            pushIntToStack(
                    visitor,
                    getWireType(property)
            );
            createIfStatement(
                    visitor,
                    Opcodes.IF_ICMPEQ,
                    methodVisitor -> createSafePackedPropertyDeserializer(
                            methodVisitor,
                            property,
                            inputStreamId,
                            fieldId,
                            false
                    ),
                    methodVisitor -> throwInvalidTag(methodVisitor, tagId)
            );
            return;
        }

        createSwitchStatement(
                visitor,
                new int[]{WIRE_TYPE_LENGTH_DELIMITED, WIRE_TYPE_VAR_INT},
                index -> createPackedPropertyDeserializer(visitor, property, inputStreamId, fieldId, index),
                switchVisitor -> throwInvalidTag(switchVisitor, tagId)
        );
    }

    // Creates a deserializer for a packed property
    private void createPackedPropertyDeserializer(MethodVisitor visitor, ProtobufPropertyStub property, int inputStreamId, int fieldId, int index) {
        switch (index) {
            case WIRE_TYPE_LENGTH_DELIMITED -> createSafePackedPropertyDeserializer(
                    visitor,
                    property,
                    inputStreamId,
                    fieldId,
                    true
            );
            case WIRE_TYPE_VAR_INT -> createSafePackedPropertyDeserializer(
                    visitor,
                    property,
                    inputStreamId,
                    fieldId,
                    false
            );
            default -> throw new IllegalArgumentException("Unknown index: " + index);
        }
    }

    // Creates a deserializer for a packed property assuming that the wire type is correct
    private void createSafePackedPropertyDeserializer(MethodVisitor visitor, ProtobufPropertyStub property, int inputStreamId, int fieldId, boolean packedWireType) {
        try {
            visitor.visitVarInsn(
                    Opcodes.ALOAD,
                    inputStreamId
            );
            var method = getDeserializerStreamMethod(property);
            visitor.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    Type.getType(ProtobufInputStream.class).getInternalName(),
                    method,
                    Type.getMethodDescriptor(ProtobufInputStream.class.getMethod(method)),
                    false
            );
            if (!property.repeated()) {
                visitor.visitVarInsn(
                        Opcodes.ASTORE,
                        fieldId
                );
                return;
            }

            visitor.visitVarInsn(
                    Opcodes.ALOAD,
                    fieldId
            );
            visitor.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    packedWireType ? property.wrapperType().getInternalName() : property.javaType().getInternalName(),
                    packedWireType ? "addAll" : "add",
                    Type.getMethodDescriptor(packedWireType ? Collection.class.getMethod("addAll", Collection.class) : Collection.class.getMethod("add", Object.class)),
                    false
            );
        }catch (NoSuchMethodException exception) {
            throw new RuntimeException("Cannot create packed deserializer", exception);
        }
    }

    // Returns the method to use to deserialize a property from ProtobufInputStream
    private String getDeserializerStreamMethod(ProtobufPropertyStub annotation) {
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
    private void createPropertyDeserializer(MethodVisitor visitor, ProtobufPropertyStub property, int inputStreamId, int tagId, int fieldId, Consumer<MethodVisitor> preparer, Consumer<MethodVisitor> finalizer) {
        visitor.visitVarInsn(
                Opcodes.ILOAD,
                tagId
        );
        pushIntToStack(
                visitor,
                getWireType(property)
        );
        createIfStatement(
                visitor,
                Opcodes.IF_ICMPEQ,
                methodVisitor -> createSafePropertyDeserializer(
                        methodVisitor,
                        property,
                        inputStreamId,
                        fieldId,
                        preparer,
                        finalizer
                ),
                methodVisitor -> throwInvalidTag(methodVisitor, tagId)
        );
    }

    // Pushes an int to the stack using the best operator
    private void pushIntToStack(MethodVisitor methodVisitor, int value) {
        switch (value) {
            case -1 -> methodVisitor.visitInsn(Opcodes.ICONST_M1);
            case 0 -> methodVisitor.visitInsn(Opcodes.ICONST_0);
            case 1 -> methodVisitor.visitInsn(Opcodes.ICONST_1);
            case 2 -> methodVisitor.visitInsn(Opcodes.ICONST_2);
            case 3 -> methodVisitor.visitInsn(Opcodes.ICONST_3);
            case 4 -> methodVisitor.visitInsn(Opcodes.ICONST_4);
            case 5 -> methodVisitor.visitInsn(Opcodes.ICONST_5);
            default -> methodVisitor.visitIntInsn(Opcodes.BIPUSH, value);
        }
    }

    // Returns the wire-type of a property
    private int getWireType(ProtobufPropertyStub annotation) {
        return switch (annotation.protoType()) {
            case MESSAGE, STRING, BYTES -> WIRE_TYPE_LENGTH_DELIMITED;
            case ENUM, BOOL, INT32, SINT32, UINT32, INT64, SINT64, UINT64 -> WIRE_TYPE_VAR_INT;
            case FLOAT, FIXED32, SFIXED32 -> WIRE_TYPE_FIXED32;
            case DOUBLE, FIXED64, SFIXED64 -> WIRE_TYPE_FIXED64;
        };
    }

    // Creates a message deserializer assuming that the wire tag is correct
    private void createSafePropertyDeserializer(MethodVisitor visitor, ProtobufPropertyStub property, int inputStreamId, int fieldId, Consumer<MethodVisitor> preparer, Consumer<MethodVisitor> finalizer) {
        try {
            if(preparer != null) {
                preparer.accept(visitor);
            }
            visitor.visitVarInsn(
                    Opcodes.ALOAD,
                    inputStreamId
            );
            visitor.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    Type.getType(ProtobufInputStream.class).getInternalName(),
                    "readBytes",
                    Type.getMethodDescriptor(ProtobufInputStream.class.getMethod("readBytes")),
                    false
            );
            if(finalizer != null) {
                finalizer.accept(visitor);
            }
            if (!property.repeated()) {
                visitor.visitVarInsn(
                        Opcodes.ASTORE,
                        fieldId
                );
                return;
            }

            visitor.visitVarInsn(
                    Opcodes.ALOAD,
                    fieldId
            );
            visitor.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    property.wrapperType().getInternalName(),
                    "add",
                    Type.getMethodDescriptor(Collection.class.getMethod("add", Object.class)),
                    false
            );
        }catch (NoSuchMethodException exception) {
            throw new RuntimeException("Cannot create message deserializer", exception);
        }
    }

    // Throws a ProtobufDeserializationException.invalidTag exception with tagId as a parameter
    private void throwInvalidTag(MethodVisitor localCreator, int tagId) {
        try {
            localCreator.visitVarInsn(
                    Opcodes.ILOAD,
                    tagId
            );
            localCreator.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    Type.getType(ProtobufDeserializationException.class).getInternalName(),
                    "invalidTag",
                    Type.getMethodDescriptor(ProtobufDeserializationException.class.getMethod("invalidTag", int.class)),
                    false
            );
            localCreator.visitInsn(
                    Opcodes.ATHROW
            );
        }catch (NoSuchMethodException exception) {
            throw new RuntimeException("Cannot write invalid tag", exception);
        }
    }

    // Creates the body of the deserializer for an Enum
    // It looks like this:
    // Optional<Type> of(int index) = Arrays.stream(values()).filter(entry -> entry.index == index).findFirst();
    private void createEnumDeserializer(MethodVisitor methodVisitor) {
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
        var predicateLambdaMethod = cv.visitMethod(
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
                visitor -> {
                    predicateLambdaMethod.visitInsn(Opcodes.ICONST_0);
                    predicateLambdaMethod.visitInsn(Opcodes.IRETURN);
                },
                visitor -> {
                    visitor.visitInsn(Opcodes.ICONST_1);
                    visitor.visitInsn(Opcodes.IRETURN);
                }
        );
        predicateLambdaMethod.visitMaxs(
                -1,
                -1
        );
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
    private void createWhileStatement(MethodVisitor visitor, int operator, Consumer<MethodVisitor> preparer, BiConsumer<MethodVisitor, Label> whileBranch, Consumer<MethodVisitor> outerBranch) {
        var whileOuterLabel = new Label();
        visitor.visitLabel(whileOuterLabel);
        preparer.accept(visitor);
        var whileInnerLabel = new Label();
        visitor.visitJumpInsn(operator, whileInnerLabel);
        outerBranch.accept(visitor);
        visitor.visitLabel(whileInnerLabel);
        whileBranch.accept(visitor, whileOuterLabel);
        visitor.visitJumpInsn(Opcodes.GOTO, whileOuterLabel);
    }

    // Creates an if statement
    private void createIfStatement(MethodVisitor visitor, int instruction, Consumer<MethodVisitor> trueBranch, Consumer<MethodVisitor> falseBranch) {
        var trueBranchLabel = new Label();
        visitor.visitJumpInsn(instruction, trueBranchLabel);
        falseBranch.accept(visitor);
        visitor.visitLabel(trueBranchLabel);
        trueBranch.accept(visitor);
    }

    // Creates a switch statement
    private void createSwitchStatement(MethodVisitor visitor, int[] knownBranches, IntConsumer knownBranch, Consumer<MethodVisitor> defaultBranch) {
        var unknownPropertyLabel = new Label();
        var labels = IntStream.range(0, knownBranches.length)
                .mapToObj(ignored -> new Label())
                .toArray(Label[]::new);
        visitor.visitLookupSwitchInsn(
                unknownPropertyLabel,
                knownBranches,
                labels
        );
        visitor.visitLabel(unknownPropertyLabel);
        defaultBranch.accept(visitor);
        for(var index = 0; index < labels.length; index++) {
            var propertyLabel = labels[index];
            visitor.visitLabel(propertyLabel);
            knownBranch.accept(index);
        }
    }
}

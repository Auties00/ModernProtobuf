package it.auties.protobuf.serialization.instrumentation;

import it.auties.protobuf.Protobuf;
import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.model.ProtobufVersion;
import it.auties.protobuf.serialization.model.ProtobufMessageElement;
import it.auties.protobuf.serialization.model.ProtobufPropertyStub;
import it.auties.protobuf.stream.ProtobufInputStream;
import org.objectweb.asm.*;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
        return "(%s[B)%s".formatted(versionType.getDescriptor(), element.classType());
    }
    
    @Override
    protected String signature() {
        if (!element.isEnum()) {
            return null;
        }

        return "(I)Ljava/util/Optional<%s>;".formatted(element.classType());
    }

    // Creates the body of the deserializer for a message
    private void createMessageDeserializer() {
        var inputStreamId = createInputStream();
        var properties = createdLocalDeserializedVariables();
        var loopStart = new Label();
        methodVisitor.visitLabel(loopStart);
        pushHasNextTagToStack(inputStreamId);
        var loopBody = new Label();
        methodVisitor.visitJumpInsn(Opcodes.IFNE, loopBody);
        createReturnDeserializedValue(properties);
        methodVisitor.visitLabel(loopBody);
        createPropertiesDeserializer(loopStart, properties, inputStreamId);
    }

    // Creates the body of the while method to deserialize properties
    private void createPropertiesDeserializer(Label loopLabel, Map<ProtobufPropertyStub, Integer> properties, int inputStreamId) {
        pushPropertyIndexToStack(inputStreamId);
        var unknownPropertyLabel = new Label();
        var indexes = getPropertiesIndexes();
        var knownLabels = IntStream.range(0, indexes.length)
                .mapToObj(ignored -> new Label())
                .toArray(Label[]::new);
        methodVisitor.visitLookupSwitchInsn(
                unknownPropertyLabel,
                indexes,
                knownLabels
        );
        methodVisitor.visitLabel(unknownPropertyLabel);
        createUnknownPropertyDeserializer(loopLabel);
        for(var index = 0; index < knownLabels.length; index++) {
            methodVisitor.visitLabel(knownLabels[index]);
            createKnownPropertyDeserializer(loopLabel, properties, inputStreamId, index);
        }
    }

    // Pushes the property's index to the stack
    // inputStream.index()
    private void pushPropertyIndexToStack(int inputStreamId) {
        try {
            methodVisitor.visitVarInsn(
                    Opcodes.ALOAD,
                    inputStreamId
            );
            methodVisitor.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    Type.getType(ProtobufInputStream.class).getInternalName(),
                    "index",
                    Type.getMethodDescriptor(ProtobufInputStream.class.getMethod("index")),
                    false
            );
        }catch (NoSuchMethodException exception) {
            throw new RuntimeException("Cannot create index reader", exception);
        }
    }

    // Creates a deserializer for a property that is in the model
    private void createKnownPropertyDeserializer(Label loopLabel, Map<ProtobufPropertyStub, Integer> properties, int inputStreamId, int index) {
        try {
            var property = element.properties().get(index);
            var fieldId = properties.get(property);
            var repeated = property.repeated();
            if(repeated) {
                methodVisitor.visitVarInsn(
                        getLoadInstruction(property.fieldType()),
                        fieldId
                );
            }
            createStreamDeserialization(property, inputStreamId);
            if(property.protoType() == ProtobufType.MESSAGE) {
                deserializeObject(property);
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
                        getStoreInstruction(property.fieldType()),
                        fieldId
                );
            }
            methodVisitor.visitJumpInsn(
                    Opcodes.GOTO,
                    loopLabel
            );
        }catch (NoSuchMethodException exception) {
            throw new RuntimeException("Cannot create message deserializer", exception);
        }
    }

    private void createStreamDeserialization(ProtobufPropertyStub property, int inputStreamId) throws NoSuchMethodException {
        methodVisitor.visitVarInsn(
                Opcodes.ALOAD,
                inputStreamId
        );
        var method = getDeserializerStreamMethod(
                property
        );
        methodVisitor.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                Type.getType(ProtobufInputStream.class).getInternalName(),
                method,
                Type.getMethodDescriptor(ProtobufInputStream.class.getMethod(method)),
                false
        );
    }

    // Returns the method to use to deserialize a property from ProtobufInputStream
    private String getDeserializerStreamMethod(ProtobufPropertyStub annotation) {
        if(annotation.isEnum()) {
            return annotation.packed() ? "readInt32Packed" : "readInt32";
        }

        return switch (annotation.protoType()) {
            case STRING -> "readString";
            case MESSAGE, BYTES -> "readBytes";
            case BOOL -> annotation.packed() ? "readBoolPacked" : "readBool";
            case INT32, SINT32, UINT32 -> annotation.packed() ? "readInt32Packed" : "readInt32";
            case FLOAT -> annotation.packed() ? "readFloatPacked" : "readFloat";
            case DOUBLE -> annotation.packed() ? "readDoublePacked" : "readDouble";
            case FIXED32, SFIXED32 -> annotation.packed() ? "readFixed32Packed" : "readFixed32";
            case INT64, SINT64, UINT64 -> annotation.packed() ? "readInt64Packed" :  "readInt64";
            case FIXED64, SFIXED64 -> annotation.packed() ? "readFixed64Packed" : "readFixed64";
        };
    }

    private void deserializeObject(ProtobufPropertyStub property) {
        if (property.isEnum()) {
            deserializeEnumFromInt(property);
            return;
        }

        deserializeMessageFromBytes(property);
    }

    // Creates an instruction to break out of the switch statement as the property isn't known
    // According to the Protobuf spec, unknown properties shouldn't throw an error
    private void createUnknownPropertyDeserializer(Label loopLabel) {
        methodVisitor.visitJumpInsn(
                Opcodes.GOTO,
                loopLabel
        );
    }

    // Returns an array of all known indexes
    private int[] getPropertiesIndexes() {
        return element.properties()
                .stream()
                .mapToInt(ProtobufPropertyStub::index)
                .toArray();
    }

    // Returns the deserialized value by calling the message's constructor
    // it also checks that all required fields are filled
    private void createReturnDeserializedValue(LinkedHashMap<ProtobufPropertyStub, Integer> properties) {
        properties.entrySet()
                .stream()
                .filter(entry -> entry.getKey().required())
                .forEach(entry -> applyNullCheck(entry.getKey(), entry.getValue()));
        methodVisitor.visitTypeInsn(
                Opcodes.NEW,
                element.classType().getInternalName()
        );
        methodVisitor.visitInsn(
                Opcodes.DUP
        );
        var constructorArgsDescriptor = properties.entrySet()
                .stream()
                .peek(entry -> methodVisitor.visitVarInsn(getLoadInstruction(entry.getKey().fieldType()), entry.getValue()))
                .map(entry -> entry.getKey().javaType().getDescriptor())
                .collect(Collectors.joining(""));
        methodVisitor.visitMethodInsn(
                Opcodes.INVOKESPECIAL,
                element.classType().getInternalName(),
                "<init>",
                "(%s)V".formatted(constructorArgsDescriptor),
                false
        );
        methodVisitor.visitInsn(
                Opcodes.ARETURN
        );
    }

    // Reads the wire tag and index of the next field from the input stream
    private void pushHasNextTagToStack(int inputStreamId) {
        try {
            methodVisitor.visitVarInsn(
                    Opcodes.ALOAD,
                    inputStreamId
            );
            methodVisitor.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    Type.getType(ProtobufInputStream.class).getInternalName(),
                    "readTag",
                    Type.getMethodDescriptor(ProtobufInputStream.class.getMethod("readTag")),
                    false
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
            var id = createLocalVariable(inputStreamType);
            methodVisitor.visitVarInsn(
                    Opcodes.ASTORE,
                    id
            );
            return id;
        }catch (NoSuchMethodException throwable) {
            throw new RuntimeException("Cannot create input stream var", throwable);
        }
    }

    // Deserializes an Enum or null object from an index
    private void deserializeEnumFromInt(ProtobufPropertyStub property) {
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
                "(Ljava/lang/Object;)%s".formatted(element.classType()),
                false
        );
    }

    // Deserializes a message from bytes
    private void deserializeMessageFromBytes(ProtobufPropertyStub property) {
        methodVisitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                property.javaType().getInternalName(),
                Protobuf.DESERIALIZATION_CLASS_METHOD,
                "([B)L%s;".formatted(property.javaType().getInternalName()),
                false
        );
    }

    // Invokes Objects.requireNonNull on a required field
    private void applyNullCheck(ProtobufPropertyStub property, int localVariableId) {
        try {
            methodVisitor.visitVarInsn(
                    Opcodes.ALOAD,
                    getLoadInstruction(property.fieldType())
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
    private LinkedHashMap<ProtobufPropertyStub, Integer> createdLocalDeserializedVariables() {
        return element.properties()
                .stream()
                .map(entry -> Map.entry(entry, createLocalDeserializeVariable(entry)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first, LinkedHashMap::new));
    }

    // Creates a local variable to store a property's deserialized value
    private int createLocalDeserializeVariable(ProtobufPropertyStub property) {
        pushDeserializedValueDefaultValueToStack(property);
        var id = createLocalVariable(property.fieldType());
        methodVisitor.visitVarInsn(getStoreInstruction(property.fieldType()), id);
        return id;
    }

    private int getLoadInstruction(Type type) {
        return switch (type.getSort()) {
            case Type.OBJECT, Type.ARRAY -> Opcodes.ALOAD;
            case Type.INT, Type.BOOLEAN, Type.CHAR, Type.SHORT, Type.BYTE -> Opcodes.ILOAD;
            case Type.FLOAT -> Opcodes.FLOAD;
            case Type.DOUBLE -> Opcodes.DLOAD;
            case Type.LONG -> Opcodes.LLOAD;
            default -> throw new RuntimeException("Unexpected type: " + type.getClassName());
        };
    }

    private int getStoreInstruction(Type type) {
        return switch (type.getSort()) {
            case Type.OBJECT, Type.ARRAY -> Opcodes.ASTORE;
            case Type.INT, Type.BOOLEAN, Type.CHAR, Type.SHORT, Type.BYTE -> Opcodes.ISTORE;
            case Type.FLOAT -> Opcodes.FSTORE;
            case Type.DOUBLE -> Opcodes.DSTORE;
            case Type.LONG -> Opcodes.LSTORE;
            default -> throw new RuntimeException("Unexpected type: " + type.getClassName());
        };
    }

    // Pushes to the stack the correct default value for the local variable created by createDeserializedField
    private void pushDeserializedValueDefaultValueToStack(ProtobufPropertyStub property) {
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

    // Creates the body of the deserializer for an Enum
    // It looks like this:
    // Optional<Type> of(int index) = Arrays.stream(values()).filter(entry -> entry.index == index).findFirst();
    private void createEnumDeserializer() {
        try {
            methodVisitor.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    element.classType().getInternalName(),
                    "values",
                    "()[%s".formatted(element.classType()),
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
        var lambdaDescriptor = "(I%s)Z".formatted(element.classType());
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
                element.classType().getInternalName(),
                "index",
                "I"
        );
        predicateLambdaMethod.visitVarInsn(
                Opcodes.ILOAD,
                0
        );
        var trueBranchLabel = new Label();
        predicateLambdaMethod.visitJumpInsn(Opcodes.IF_ICMPNE, trueBranchLabel);
        predicateLambdaMethod.visitInsn(Opcodes.ICONST_1);
        predicateLambdaMethod.visitInsn(Opcodes.IRETURN);
        predicateLambdaMethod.visitLabel(trueBranchLabel);
        predicateLambdaMethod.visitInsn(Opcodes.ICONST_0);
        predicateLambdaMethod.visitInsn(Opcodes.IRETURN);
        predicateLambdaMethod.visitMaxs(-1,-1);
        predicateLambdaMethod.visitEnd();
        return new Object[]{
                Type.getType("(Ljava/lang/Object;)Z"),
                new Handle(
                        Opcodes.H_INVOKESTATIC,
                        element.classType().getInternalName(),
                        lambdaName,
                        lambdaDescriptor,
                        false
                ),
                Type.getType("(I)Z")
        };
    }
}

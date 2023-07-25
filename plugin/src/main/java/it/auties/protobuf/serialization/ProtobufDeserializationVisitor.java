package it.auties.protobuf.serialization;

import it.auties.protobuf.base.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.LocalVariablesSorter;

import java.lang.System.Logger;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class ProtobufDeserializationVisitor extends ClassVisitor {
    private final ProtobufMessageElement element;
    private final Logger logger;

    protected ProtobufDeserializationVisitor(ProtobufMessageElement element, ClassVisitor classVisitor) {
        super(Opcodes.ASM9);
        this.element = element;
        this.cv = classVisitor;
        this.logger = System.getLogger("Protobuf");
    }

    @Override
    public void visitEnd() {
        var methodAccess = Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC;
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
            writeNamedEnumConstructor(methodVisitor);
            return;
        }

        writeClassConstructor(methodAccess, methodName, methodDescriptor, methodVisitor);
    }

    private String getMethodName() {
        return element.isEnum() ? ProtobufMessage.DESERIALIZATION_ENUM_METHOD
                : ProtobufMessage.DESERIALIZATION_CLASS_METHOD;
    }

    private String getMethodDescriptor() {
        return element.isEnum() ? "(I)Ljava/util/Optional;" : "([B)L%s;".formatted(element.className());
    }

    private String getMethodSignature() {
        if (!element.isEnum()) {
            return null;
        }

        return "(I)Ljava/util/Optional<L%s;>;".formatted(element.className());
    }

    private void writeClassConstructor(int access, String methodName, String methodDescriptor, MethodVisitor methodVisitor) {
        var localCreator = new GeneratorAdapter(
                methodVisitor,
                access,
                methodName,
                methodDescriptor
        );
        var inputStreamLocalId = createInputStreamVar(localCreator);
        var properties = createDeserializedFields(localCreator);
        applyNullChecks(localCreator, properties);
        applyConstructorCall(localCreator, properties);
        cv.visitEnd();
    }

    private void applyConstructorCall(LocalVariablesSorter localCreator, TreeMap<Integer, ProtobufPropertyStub> properties) {
        localCreator.visitTypeInsn(
                Opcodes.NEW,
                element.className()
        );
        localCreator.visitInsn(
                Opcodes.DUP
        );
        var constructorArgsDescriptor = properties.entrySet()
                .stream()
                .peek(entry -> localCreator.visitVarInsn(Opcodes.ALOAD, entry.getKey()))
                .map(entry -> entry.getValue().javaType().getDescriptor())
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

    private void applyNullChecks(MethodVisitor methodVisitor, TreeMap<Integer, ProtobufPropertyStub> properties) {
        properties.entrySet()
                .stream()
                .filter(entry -> entry.getValue().required())
                .forEach(entry -> applyNullCheck(methodVisitor, entry.getValue(), entry.getKey()));
    }

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

    private TreeMap<Integer, ProtobufPropertyStub> createDeserializedFields(LocalVariablesSorter localCreator) {
        return element.properties()
                .stream()
                .map(entry -> Map.entry(addDeserializer(localCreator, entry), entry))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first, TreeMap::new));
    }

    private int addDeserializer(LocalVariablesSorter localCreator, ProtobufPropertyStub property) {
        addDeserializerDefaultValue(localCreator, property);
        var variableId = localCreator.newLocal(property.javaType());
        localCreator.visitVarInsn(
                Opcodes.ASTORE,
                variableId
        );
        return variableId;
    }

    private void addDeserializerDefaultValue(LocalVariablesSorter localCreator, ProtobufPropertyStub property) {
        if(property.wrapperType() != null){
            checkRepeatedNonAbstractWrapper(property);
            localCreator.visitTypeInsn(
                    Opcodes.NEW,
                    property.javaType().getInternalName()
            );
            localCreator.visitInsn(
                    Opcodes.DUP
            );
            localCreator.visitMethodInsn(
                    Opcodes.INVOKESPECIAL,
                    property.javaType().getInternalName(),
                    "<init>",
                    "()V",
                    false
            );
            return;
        }

        var sort = property.javaType().getSort();
        if (sort == Type.ARRAY || sort == Type.OBJECT) {
            localCreator.visitInsn(Opcodes.ACONST_NULL);
            return;
        }

        localCreator.visitInsn(Opcodes.ICONST_0);
    }

    private void checkRepeatedNonAbstractWrapper(ProtobufPropertyStub property) {
        var className = property.wrapperType().getClassName();
        var rawClassName = className.substring(0, className.indexOf("<"));
        try {
            var javaClass = Class.forName(rawClassName);
            if (!Modifier.isAbstract(javaClass.getModifiers())) {
                return;
            }

            throw new IllegalArgumentException("%s %s is abstract: this is not allowed for repeated types!".formatted(rawClassName, property.name()));
        }catch (ClassNotFoundException exception) {
            logger.log(Logger.Level.WARNING, "Cannot check whether %s is a concrete type as it's not part of the std Java library".formatted(rawClassName));
        }
    }

    private int createInputStreamVar(LocalVariablesSorter localCreator) {
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
            localCreator.visitMethodInsn(
                    Opcodes.INVOKESPECIAL,
                    inputStreamType.getInternalName(),
                    "<init>",
                    Type.getConstructorDescriptor(ProtobufInputStream.class.getConstructor(byte[].class)),
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

    private void writeNamedEnumConstructor(MethodVisitor methodVisitor) {
        try {
            methodVisitor.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    element.className(),
                    "values",
                    "()[L" + element.className() + ";",
                    false
            );

            methodVisitor.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    Type.getType(Arrays.class).getInternalName(),
                    "stream",
                    Type.getMethodDescriptor(Arrays.class.getMethod("stream", Object[].class)),
                    false
            );

            var lambdaParameters = createEnumConstructorLambda();
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
                    lambdaParameters
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

            methodVisitor.visitEnd();

            cv.visitEnd();
        } catch (NoSuchMethodException throwable) {
            throw new RuntimeException("Cannot instrument enum", throwable);
        }
    }

    private Object[] createEnumConstructorLambda() {
        var methodName = "lambda$of$0";
        var methodDescriptor = "(IL%s;)Z".formatted(element.className());
        var predicateLambdaMethod = cv.visitMethod(
                Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC + Opcodes.ACC_SYNTHETIC,
                methodName,
                methodDescriptor,
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

        var falseLabel = new Label();
        predicateLambdaMethod.visitJumpInsn(
                Opcodes.IF_ICMPNE,
                falseLabel
        );
        predicateLambdaMethod.visitInsn(
                Opcodes.ICONST_1
        );
        predicateLambdaMethod.visitInsn(
                Opcodes.IRETURN
        );
        predicateLambdaMethod.visitLabel(
                falseLabel
        );
        predicateLambdaMethod.visitInsn(
                Opcodes.ICONST_0
        );
        predicateLambdaMethod.visitInsn(
                Opcodes.IRETURN
        );
        predicateLambdaMethod.visitEnd();
        return new Object[]{
                Type.getType("(Ljava/lang/Object;)Z"),
                new Handle(
                        Opcodes.H_INVOKESTATIC,
                        element.className(),
                        methodName,
                        methodDescriptor,
                        false
                ),
                Type.getType("(I)Z")
        };
    }
}

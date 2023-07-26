package it.auties.protobuf.serialization;

import it.auties.protobuf.Protobuf;
import it.auties.protobuf.model.ProtobufVersion;
import it.auties.protobuf.stream.ProtobufOutputStream;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.LocalVariablesSorter;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Objects;

class ProtobufSerializationVisitor extends ClassVisitor {
    private final ProtobufMessageElement element;

    protected ProtobufSerializationVisitor(ProtobufMessageElement element, ClassVisitor classVisitor) {
        super(Opcodes.ASM9);
        this.element = element;
        this.cv = classVisitor;
    }

    @Override
    public void visitEnd() {
        var methodAccess = getMethodAccess();
        var methodName = getMethodName();
        var methodDescriptor = getMethodDescriptor();
        var methodVisitor = cv.visitMethod(
                methodAccess,
                methodName,
                methodDescriptor,
                null,
                new String[0]
        );
        methodVisitor.visitCode();
        createMessageSerializer(methodVisitor, methodAccess, methodName, methodDescriptor);
        methodVisitor.visitMaxs(-1, -1);
        methodVisitor.visitEnd();
        cv.visitEnd();
    }

    // Returns the modifiers for the serializer
    private int getMethodAccess() {
        return Opcodes.ACC_PUBLIC;
    }

    // Returns the name of the serializer
    private String getMethodName() {
        return Protobuf.SERIALIZATION_METHOD;
    }

    // Returns the descriptor of the serializer
    private String getMethodDescriptor() {
        var versionType = Type.getType(ProtobufVersion.class);
        return "(L%s;)[B".formatted(versionType.getInternalName());
    }

    // Creates the body of the serializer for a message
    private void createMessageSerializer(MethodVisitor methodVisitor, int access, String methodName, String methodDescriptor) {
        var localCreator = new GeneratorAdapter(
                methodVisitor,
                access,
                methodName,
                methodDescriptor
        );
        var outputStreamId = createOutputStream(localCreator);
        writeProperties(methodVisitor, localCreator, outputStreamId);
        createReturnSerializedValue(methodVisitor, localCreator, outputStreamId);
    }

    // Writes all properties to the output stream
    private void writeProperties(MethodVisitor methodVisitor, GeneratorAdapter localCreator, int outputStreamId) {
        element.properties()
                .forEach(property -> writeProperty(methodVisitor, localCreator, outputStreamId, property));
    }

    // Writes a property to the output stream
    private void writeProperty(MethodVisitor methodVisitor, GeneratorAdapter localCreator, int outputStreamId, ProtobufPropertyStub property) {
        methodVisitor.visitVarInsn(
                Opcodes.ALOAD,
                outputStreamId
        );
        pushIntToStack(
                methodVisitor,
                property.index()
        );
        methodVisitor.visitFieldInsn(
                Opcodes.GETFIELD,
                element.className(),
                property.name(),
                Objects.requireNonNullElse(property.wrapperType(), property.javaType()).getDescriptor()
        );
        var readMethod = getSerializerStreamMethod(property);
        localCreator.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                Type.getType(ProtobufOutputStream.class).getInternalName(),
                readMethod.getName(),
                Type.getMethodDescriptor(readMethod),
                false
        );
    }

    // Returns the serialized value
    private void createReturnSerializedValue(MethodVisitor methodVisitor, GeneratorAdapter localCreator, int outputStreamId) {
       try {
           methodVisitor.visitVarInsn(
                   Opcodes.ALOAD,
                   outputStreamId
           );
           localCreator.visitMethodInsn(
                   Opcodes.INVOKEVIRTUAL,
                   Type.getType(ProtobufOutputStream.class).getInternalName(),
                   "toByteArray",
                   Type.getMethodDescriptor(ProtobufOutputStream.class.getMethod("toByteArray")),
                   false
           );
           localCreator.visitInsn(
                   Opcodes.ARETURN
           );
       }catch (NoSuchMethodException exception) {
           throw new RuntimeException("Missing toByteArray method", exception);
       }
    }

    // Returns the method to use to deserialize a property from ProtobufInputStream
    private Method getSerializerStreamMethod(ProtobufPropertyStub annotation) {
        var clazz = ProtobufOutputStream.class;
        try {
            return switch (annotation.protoType()) {
                case MESSAGE, BYTES, STRING -> {
                    if (annotation.packed()) {
                        throw new IllegalArgumentException("%s %s is packed: only scalar types are allowed to have this modifier".formatted(annotation.protoType().name(), annotation.name()));
                    }

                    if (annotation.repeated()) {
                        yield clazz.getMethod("writeBytes", int.class, Collection.class);
                    }

                    yield clazz.getMethod("writeBytes", int.class, byte[].class);
                }
                case ENUM, INT32, SINT32, BOOL -> {
                    if (annotation.repeated()) {
                        yield clazz.getMethod("writeInt32", int.class, Collection.class);
                    }

                    yield clazz.getMethod("writeInt32", int.class, Integer.class);
                }
                case UINT32 -> {
                    if (annotation.repeated()) {
                        yield clazz.getMethod("writeUInt32", int.class, Collection.class);
                    }

                    yield clazz.getMethod("writeUInt32", int.class, Integer.class);
                }
                case FLOAT -> {
                    if (annotation.repeated()) {
                        yield clazz.getMethod("writeFloat", int.class, Collection.class);
                    }

                    yield clazz.getMethod("writeFloat", int.class, Float.class);
                }
                case DOUBLE -> {
                    if (annotation.repeated()) {
                        yield clazz.getMethod("writeDouble", int.class, Collection.class);
                    }

                    yield clazz.getMethod("writeDouble", int.class, Double.class);
                }
                case FIXED32, SFIXED32 -> {
                    if (annotation.repeated()) {
                        yield clazz.getMethod("writeFixed32", int.class, Collection.class);
                    }

                    yield clazz.getMethod("writeFixed32", int.class, Integer.class);
                }
                case INT64, SINT64 -> {
                    if (annotation.repeated()) {
                        yield clazz.getMethod("writeInt64", int.class, Collection.class);
                    }

                    yield clazz.getMethod("writeInt64", int.class, Long.class);
                }
                case UINT64 -> {
                    if (annotation.repeated()) {
                        yield clazz.getMethod("writeUInt64", int.class, Collection.class);
                    }

                    yield clazz.getMethod("writeUInt64", int.class, Long.class);
                }
                case FIXED64, SFIXED64 -> {
                    if (annotation.repeated()) {
                        yield clazz.getMethod("writeFixed64", int.class, Collection.class);
                    }

                    yield clazz.getMethod("writeFixed64", int.class, Long.class);
                }
            };
        }catch (NoSuchMethodException exception) {
            throw new RuntimeException("Missing serializer method", exception);
        }
    }

    // Creates a ProtobufOutputStream from the first parameter of the method(ProtobufVersion)
    private int createOutputStream(LocalVariablesSorter localCreator) {
        try {
            var outputStreamType = Type.getType(ProtobufOutputStream.class);
            localCreator.visitTypeInsn(
                    Opcodes.NEW,
                    outputStreamType.getInternalName()
            );
            localCreator.visitInsn(
                    Opcodes.DUP
            );
            localCreator.visitVarInsn(
                    Opcodes.ALOAD,
                    1
            );
            localCreator.visitMethodInsn(
                    Opcodes.INVOKESPECIAL,
                    outputStreamType.getInternalName(),
                    "<init>",
                    Type.getConstructorDescriptor(ProtobufOutputStream.class.getConstructor(ProtobufVersion.class)),
                    false
            );
            var streamLocalId = localCreator.newLocal(outputStreamType);
            localCreator.visitVarInsn(
                    Opcodes.ASTORE,
                    streamLocalId
            );
            return streamLocalId;
        }catch (NoSuchMethodException throwable) {
            throw new RuntimeException("Cannot create input stream var", throwable);
        }
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
}

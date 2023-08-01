package it.auties.protobuf.serialization.instrumentation;

import it.auties.protobuf.serialization.model.ProtobufMessageElement;
import it.auties.protobuf.serialization.model.ProtobufPropertyStub;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.LocalVariablesSorter;

import java.util.Objects;
import java.util.Optional;

public abstract class ProtobufInstrumentationVisitor {
    protected final ProtobufMessageElement element;
    protected final ClassWriter classWriter;
    protected LocalVariablesSorter methodVisitor;

    protected ProtobufInstrumentationVisitor(ProtobufMessageElement element, ClassWriter classWriter) {
        this.element = element;
        this.classWriter = classWriter;
    }

    public void instrument() {
        if(!shouldInstrument()) {
            return;
        }

        var access = access();
        var descriptor = descriptor();
        var visitor = classWriter.visitMethod(
                access,
                name(),
                descriptor,
                signature(),
                new String[0]
        );
        this.methodVisitor = new LocalVariablesSorter(
                access,
                descriptor,
                visitor
        );
        methodVisitor.visitCode();
        doInstrumentation();
        methodVisitor.visitMaxs(-1, -1);
        methodVisitor.visitEnd();
    }

    public abstract boolean shouldInstrument();

    protected abstract void doInstrumentation();

    protected abstract int access();
    protected abstract String name();
    protected abstract String descriptor();
    protected abstract String signature();

    protected void pushIntToStack(int value) {
        pushIntToStack(methodVisitor, value);
    }

    protected void pushIntToStack(MethodVisitor visitor, int value) {
        switch (value) {
            case -1 -> visitor.visitInsn(Opcodes.ICONST_M1);
            case 0 -> visitor.visitInsn(Opcodes.ICONST_0);
            case 1 -> visitor.visitInsn(Opcodes.ICONST_1);
            case 2 -> visitor.visitInsn(Opcodes.ICONST_2);
            case 3 -> visitor.visitInsn(Opcodes.ICONST_3);
            case 4 -> visitor.visitInsn(Opcodes.ICONST_4);
            case 5 -> visitor.visitInsn(Opcodes.ICONST_5);
            default -> visitor.visitIntInsn(Opcodes.BIPUSH, value);
        }
    }

    protected int createLocalVariable(Type type) {
        return methodVisitor.newLocal(type);
    }

    protected void boxValueIfNecessary(Type fieldType) {
        var type = getWrapperType(fieldType);
        if(type.isEmpty()) {
            return;
        }

        methodVisitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                type.get().getInternalName(),
                "valueOf",
                "(%s)%s".formatted(fieldType.getDescriptor(), type.get().getDescriptor()),
                false
        );
    }

    private Optional<Type> getWrapperType(Type fieldType) {
        return Optional.ofNullable(switch (fieldType.getSort()) {
            case Type.OBJECT, Type.ARRAY -> null;
            case Type.INT -> Type.getType(Integer.class);
            case Type.BOOLEAN -> Type.getType(Boolean.class);
            case Type.CHAR -> Type.getType(Character.class);
            case Type.SHORT -> Type.getType(Short.class);
            case Type.BYTE -> Type.getType(Byte.class);
            case Type.FLOAT -> Type.getType(Float.class);
            case Type.DOUBLE -> Type.getType(Double.class);
            case Type.LONG -> Type.getType(Long.class);
            default -> throw new RuntimeException("Unexpected type: " + fieldType.getClassName());
        });
    }

    protected void checkRequiredProperties() {
        element.properties()
                .stream()
                .filter(ProtobufPropertyStub::required)
                .forEach(this::createNullCheck);
    }

    // Invokes Objects.requireNonNull on a required field
    private void createNullCheck(ProtobufPropertyStub property) {
        try {
            var localVariableId = property.fieldId().get();
            if(localVariableId == 0) {
                methodVisitor.visitVarInsn(
                        Opcodes.ALOAD,
                        0 // this
                );
                methodVisitor.visitFieldInsn(
                        Opcodes.GETFIELD,
                        element.classType().getInternalName(),
                        property.name(),
                        property.fieldType().getDescriptor()
                );
            }else {
                methodVisitor.visitVarInsn(
                        getLoadInstruction(property.fieldType()),
                        localVariableId
                );
            }
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

    protected int getLoadInstruction(Type type) {
        return switch (type.getSort()) {
            case Type.OBJECT, Type.ARRAY -> Opcodes.ALOAD;
            case Type.INT, Type.BOOLEAN, Type.CHAR, Type.SHORT, Type.BYTE -> Opcodes.ILOAD;
            case Type.FLOAT -> Opcodes.FLOAD;
            case Type.DOUBLE -> Opcodes.DLOAD;
            case Type.LONG -> Opcodes.LLOAD;
            default -> throw new RuntimeException("Unexpected type: " + type.getClassName());
        };
    }

    protected int getStoreInstruction(Type type) {
        return switch (type.getSort()) {
            case Type.OBJECT, Type.ARRAY -> Opcodes.ASTORE;
            case Type.INT, Type.BOOLEAN, Type.CHAR, Type.SHORT, Type.BYTE -> Opcodes.ISTORE;
            case Type.FLOAT -> Opcodes.FSTORE;
            case Type.DOUBLE -> Opcodes.DSTORE;
            case Type.LONG -> Opcodes.LSTORE;
            default -> throw new RuntimeException("Unexpected type: " + type.getClassName());
        };
    }

    protected int getReturnInstruction(Type type) {
        return switch (type.getSort()) {
            case Type.OBJECT, Type.ARRAY -> Opcodes.ARETURN;
            case Type.INT, Type.BOOLEAN, Type.CHAR, Type.SHORT, Type.BYTE -> Opcodes.IRETURN;
            case Type.FLOAT -> Opcodes.FRETURN;
            case Type.DOUBLE -> Opcodes.DRETURN;
            case Type.LONG -> Opcodes.LRETURN;
            default -> throw new RuntimeException("Unexpected type: " + type.getClassName());
        };
    }
}

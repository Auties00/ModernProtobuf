package it.auties.protobuf.serialization.instrumentation;

import it.auties.protobuf.serialization.model.ProtobufMessageElement;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public abstract class ProtobufInstrumentationVisitor extends ClassVisitor {
    protected final ProtobufMessageElement element;
    protected  MethodVisitor methodVisitor;
    protected int localsCount;
    protected int stackSize;

    protected ProtobufInstrumentationVisitor(ProtobufMessageElement element, ClassWriter classWriter) {
        super(Opcodes.ASM9);
        this.element = element;
        this.localsCount = argsCount();
        this.cv = classWriter;
    }

    @Override
    public void visitEnd() {
        this.methodVisitor = cv.visitMethod(
                access(),
                name(),
                descriptor(),
                signature(),
                new String[0]
        );
        instrument();
        methodVisitor.visitMaxs(stackSize, localsCount);
        methodVisitor.visitEnd();
    }

    public abstract void instrument();

    protected abstract int access();
    protected abstract String name();
    protected abstract String descriptor();
    protected abstract String signature();
    protected abstract int argsCount();

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

    protected int createLocalVariable() {
        return ++localsCount;
    }
}

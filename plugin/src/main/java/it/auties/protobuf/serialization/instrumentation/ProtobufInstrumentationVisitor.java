package it.auties.protobuf.serialization.instrumentation;

import it.auties.protobuf.serialization.model.ProtobufMessageElement;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public abstract class ProtobufInstrumentationVisitor {
    protected final ProtobufMessageElement element;
    protected final ClassWriter classWriter;
    protected  MethodVisitor methodVisitor;
    protected int localsCount;

    protected ProtobufInstrumentationVisitor(ProtobufMessageElement element, ClassWriter classWriter) {
        this.element = element;
        this.localsCount = argsCount();
        this.classWriter = classWriter;
    }

    public void instrument() {
        this.methodVisitor = classWriter.visitMethod(
                access(),
                name(),
                descriptor(),
                signature(),
                new String[0]
        );
        methodVisitor.visitCode();
        doInstrumentation();
        methodVisitor.visitMaxs(-1, -1);
        methodVisitor.visitEnd();
    }

    protected abstract void doInstrumentation();

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

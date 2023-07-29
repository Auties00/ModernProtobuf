package it.auties.protobuf.serialization.instrumentation;

import it.auties.protobuf.serialization.model.ProtobufMessageElement;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.LocalVariablesSorter;

import java.util.function.Consumer;

public abstract class ProtobufInstrumentationVisitor {
    protected final ProtobufMessageElement element;
    protected final ClassWriter classWriter;
    protected LocalVariablesSorter methodVisitor;

    protected ProtobufInstrumentationVisitor(ProtobufMessageElement element, ClassWriter classWriter) {
        this.element = element;
        this.classWriter = classWriter;
    }

    public void instrument() {
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

    @SuppressWarnings("SameParameterValue")
    protected void createWhileStatement(int operator, Runnable preparer, Consumer<Label> whileBranch, Runnable outerBranch) {
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
    protected void createIfStatement(MethodVisitor visitor, int instruction, Runnable trueBranch, Runnable falseBranch) {
        var trueBranchLabel = new Label();
        visitor.visitJumpInsn(instruction, trueBranchLabel);
        falseBranch.run();
        visitor.visitLabel(trueBranchLabel);
        trueBranch.run();
    }

    protected int createLocalVariable(Type type) {
        return methodVisitor.newLocal(type);
    }
}

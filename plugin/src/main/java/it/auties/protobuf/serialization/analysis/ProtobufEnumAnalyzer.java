package it.auties.protobuf.serialization.analysis;

import it.auties.protobuf.serialization.model.ProtobufMessageElement;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.tree.MethodNode;

import java.util.Objects;

public class ProtobufEnumAnalyzer extends AnalyzerAdapter {
    private final ProtobufMessageElement element;
    private Integer index;
    public ProtobufEnumAnalyzer(ProtobufMessageElement element, MethodNode node) {
        super(Opcodes.ASM9, element.className(), node.access, node.name, node.desc, null);
        this.element = element;
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
        super.visitIntInsn(opcode, operand);
        this.index = operand;
    }

    @Override
    public void visitInsn(int opcode) {
        super.visitInsn(opcode);
        if(index != null) {
            return;
        }

        var value = readIntValue(opcode);
        if(value == null) {
            return;
        }

        this.index = value;
    }

    private Integer readIntValue(int opcode) {
        return switch (opcode) {
            case Opcodes.ICONST_M1 -> -1;
            case Opcodes.ICONST_0 -> 0;
            case Opcodes.ICONST_1 -> 1;
            case Opcodes.ICONST_2 -> 2;
            case Opcodes.ICONST_3 -> 3;
            case Opcodes.ICONST_4 -> 4;
            case Opcodes.ICONST_5 -> 5;
            default -> null;
        };
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        super.visitFieldInsn(opcode, owner, name, descriptor);
        var type = Type.getType(descriptor);
        if(!Objects.equals(type.getClassName(), element.className())) {
            return;
        }

        Objects.requireNonNull(index, "Proto constant %s in %s doesn't specify an index".formatted(name, element.className()));
        element.addConstant(index, name);
        this.index = null;
    }
}

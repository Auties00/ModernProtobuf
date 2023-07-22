package it.auties.protobuf.serialization;

import it.auties.protobuf.base.*;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.LocalVariablesSorter;

import java.util.Map;
import java.util.Objects;

class ProtobufDeserializationVisitor extends ClassVisitor {
    private final ProtobufMessageElement element;
    protected ProtobufDeserializationVisitor(ProtobufMessageElement element, ClassVisitor classVisitor) {
        super(Opcodes.ASM9);
        this.element = element;
        this.cv = classVisitor;
    }

    @Override
    public void visitEnd() {
        var methodDescriptor = "([B)L" + element.className() + ";";
        var methodVisitor = cv.visitMethod(
                Opcodes.ACC_PUBLIC,
                ProtobufMessage.DESERIALIZATION_CLASS_METHOD,
                methodDescriptor,
                null,
                new String[0]
        );
        methodVisitor.visitCode();
        var localCreator = new LocalVariablesSorter(api, methodDescriptor, methodVisitor);
        var inputStreamType = Type.getType(ProtobufInputStream.class);
        localCreator.visitTypeInsn(Opcodes.NEW, inputStreamType.getInternalName());
        localCreator.visitVarInsn(Opcodes.ALOAD, 0);
        localCreator.visitMethodInsn(Opcodes.INVOKESPECIAL, ProtobufInputStream.class.getName(), "<init>", "([B)V", false);
        localCreator.visitVarInsn(Opcodes.ASTORE, localCreator.newLocal(inputStreamType));
        element.fields().forEach((name, property) -> {
            localCreator.visitInsn(Opcodes.ACONST_NULL);
            var variableType = getLocalVariableType(property);
            localCreator.visitVarInsn(Opcodes.ASTORE, localCreator.newLocal(variableType));
        });
        cv.visitEnd();
    }

    private Type getLocalVariableType(ProtobufPropertyStub property) {
        return property.type() != ProtobufType.MESSAGE ? Type.getType(property.type().wrappedType())
                : property.implementation();
    }
}

package it.auties.protobuf.serialization;

import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufType;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.LocalVariablesSorter;

import java.util.Map;

public class ProtobufDeserializationVisitor extends ClassVisitor {
    private final String className;
    private final Map<String, Map<String, Object>> fieldsPropertyValuesMap;
    protected ProtobufDeserializationVisitor(String className, Map<String, Map<String, Object>> fieldsPropertyValuesMap, ClassVisitor classVisitor) {
        super(Opcodes.ASM9);
        this.className = className;
        this.fieldsPropertyValuesMap = fieldsPropertyValuesMap;
        this.cv = classVisitor;
    }

    @Override
    public void visitEnd() {
        var methodDescriptor = "()L" + className;
        var methodVisitor = cv.visitMethod(
                Opcodes.ACC_PUBLIC,
                ProtobufMessage.DESERIALIZATION_CLASS_METHOD,
                methodDescriptor,
                null,
                new String[0]
        );
        methodVisitor.visitCode();
        var localCreator = new LocalVariablesSorter(api, methodDescriptor, methodVisitor);
        fieldsPropertyValuesMap.forEach((fieldName, fieldProperties) -> {
            var required = (boolean) fieldProperties.get("required");
            var protobufType = (ProtobufType) fieldProperties.get("type");
            var javaType = Type.getType(protobufType.primitiveType());
            var localId = localCreator.newLocal(javaType);
            localCreator.visitInsn(Opcodes.ICONST_2);
            localCreator.visitVarInsn(Opcodes.ISTORE, localId);

        });
        cv.visitEnd();
    }
}

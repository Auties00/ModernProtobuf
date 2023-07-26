package it.auties.protobuf.serialization;

import it.auties.protobuf.model.ProtobufType;
import org.objectweb.asm.*;

import java.util.*;

class ProtobufPropertyVisitor extends AnnotationVisitor {
    private final Map<String, Object> values;
    ProtobufPropertyVisitor(Map<String, Object> values) {
        super(Opcodes.ASM9);
        this.values = values;
    }

    @Override
    public void visit(String name, Object value) {
        values.put(name, value);
        super.visit(name, value);
    }

    @Override
    public void visitEnum(String name, String descriptor, String value) {
        var enumType = Type.getType(descriptor);
        if(!Objects.equals(enumType.getClassName(), ProtobufType.class.getName())){
            super.visitEnum(name, descriptor, value);
            return;
        }

        ProtobufType.of(value)
                .ifPresent(type -> values.put(name, type));
        super.visitEnum(name, descriptor, value);
    }
}
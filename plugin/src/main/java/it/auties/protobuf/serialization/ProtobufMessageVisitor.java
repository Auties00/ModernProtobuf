package it.auties.protobuf.serialization;

import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufProperty;
import it.auties.protobuf.base.ProtobufType;
import org.objectweb.asm.*;

import java.util.*;
import java.util.stream.Collectors;

public class ProtobufMessageVisitor extends ClassVisitor {
    private String messageName;
    private final Map<String, Map<String, Object>> fieldsPropertyValuesMap;
    protected ProtobufMessageVisitor() {
        super(Opcodes.ASM9);
        this.fieldsPropertyValuesMap = new HashMap<>();
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        if(isAbstract(access)) {
            return;
        }

        if(!isProtoMessage(interfaces)){
            return;
        }

        this.messageName = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    private boolean isAbstract(int access) {
        return (access & Opcodes.ACC_INTERFACE) != 0
                || (access & Opcodes.ACC_ABSTRACT) != 0;
    }

    private boolean isProtoMessage(String[] interfaces) {
        return Arrays.stream(interfaces)
                .anyMatch(entry -> Objects.equals(entry.replaceAll("/", "."), ProtobufMessage.class.getName()));
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        if(messageName == null) {
            return super.visitField(access, name, descriptor, signature, value);
        }

        var propertyValuesMap = createAnnotationMap();
        fieldsPropertyValuesMap.put(name, propertyValuesMap);
        return new ProtobufFieldVisitor(propertyValuesMap);
    }

    private TreeMap<String, Object> createAnnotationMap() {
        return Arrays.stream(ProtobufProperty.class.getMethods())
                .map(entry -> entry.getDefaultValue() != null ? Map.entry(entry.getName(), entry.getDefaultValue()) : null)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first, TreeMap::new));
    }

    protected Optional<String> messageName() {
        return Optional.ofNullable(messageName);
    }

    protected Map<String, Map<String, Object>> fieldsPropertyValuesMap() {
        return fieldsPropertyValuesMap;
    }

    private static class ProtobufFieldVisitor extends FieldVisitor {
        private final Map<String, Object> values;
        private ProtobufFieldVisitor(Map<String, Object> values) {
            super(Opcodes.ASM9);
            this.values = values;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if(!visible){
                return super.visitAnnotation(descriptor, false);
            }

            var annotationType = Type.getType(descriptor);
            if(!Objects.equals(annotationType.getClassName(), ProtobufProperty.class.getName())) {
                return super.visitAnnotation(descriptor, false);
            }

            return new ProtobufPropertyVisitor(values);
        }
    }

    private static class ProtobufPropertyVisitor extends AnnotationVisitor {
        private final Map<String, Object> values;
        private ProtobufPropertyVisitor(Map<String, Object> values) {
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

        @Override
        public void visitEnd() {
            super.visitEnd();
        }
    }
}

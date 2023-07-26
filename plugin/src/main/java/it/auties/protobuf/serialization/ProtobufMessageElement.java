package it.auties.protobuf.serialization;

import it.auties.protobuf.model.ProtobufType;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.util.*;

class ProtobufMessageElement {
    private final ClassNode classNode;
    private final List<ProtobufPropertyStub> properties;

    private final Map<String, Integer> constants;

    public ProtobufMessageElement(ClassNode classNode) {
        this.classNode = classNode;
        this.properties = new ArrayList<>();
        this.constants = new TreeMap<>();
    }

    protected ClassNode classNode() {
        return classNode;
    }

    protected String className() {
        return classNode.name;
    }

    protected List<ProtobufPropertyStub> properties() {
        return Collections.unmodifiableList(properties);
    }

    protected boolean isEnum() {
        return (classNode.access & Opcodes.ACC_ENUM) != 0;
    }

    protected Map<String, Integer> constants() {
        return Collections.unmodifiableMap(constants);
    }

    protected void addConstant(String fieldName, int fieldIndex) {
        constants.put(fieldName, fieldIndex);
    }

    protected boolean isProtobuf() {
        return !properties().isEmpty() || !constants().isEmpty();
    }

    protected void addProperty(Type fieldType, String fieldName, Map<String, Object> values) {
        var index = (int) values.get("index");
        var type = (ProtobufType) values.get("type");
        var required = (boolean) values.get("required");
        var rawImplementation = values.get("implementation");
        var repeated = (boolean) values.get("repeated");
        var implementation = getParsedImplementationType(type, fieldType, rawImplementation, required, repeated);
        var wrapperType = getWrapperType(fieldType, repeated);
        var ignore = (boolean) values.get("ignore");
        var packed = (boolean) values.get("packed");
        properties.add(new ProtobufPropertyStub(index, fieldName, type, implementation, wrapperType, required, ignore, repeated, packed));
    }

    private Type getWrapperType(Type javaType, boolean repeated) {
        return !repeated ? null : javaType;
    }

    private Type getParsedImplementationType(ProtobufType protoType, Type javaType, Object implementation, boolean required, boolean repeated) {
        if(protoType != ProtobufType.MESSAGE && protoType != ProtobufType.ENUM && required) {
            return Type.getType(protoType.wrappedType());
        }

        var rawImplementation = castImplementationType(implementation);
        if (rawImplementation != null && !rawImplementation.getClassName().equals(Object.class.getName())) {
            return rawImplementation;
        }

        var javaTypeName = javaType.getInternalName();
        var paramsStart = javaTypeName.indexOf("<");
        if (!repeated) {
            return paramsStart == -1 ? javaType : Type.getType(javaTypeName.substring(paramsStart));
        }

        if(paramsStart == -1) {
            throw new IllegalArgumentException("Repeated fields cannot be represented by a raw type: specify a type parameter(List<Something>) or an implementation(@ProtobufProperty(implementation = Something.class))");
        }

        return Type.getType(javaTypeName.substring(paramsStart + 1, javaTypeName.indexOf(">")));
    }

    private Type castImplementationType(Object implementation) {
        if(implementation instanceof Class<?> clazz) {
            return Type.getType(clazz);
        }else if(implementation instanceof Type type) {
            return type;
        }else {
            return null;
        }
    }

    protected void checkErrors() {
        if(isEnum() && !hasIndexField()) {
            throw new IllegalArgumentException("Missing index field in enum " + className());
        }

        // TODO: Can we check if a constructor for properties exists?
        // The problem tho is that it's kind of hard to determine if a type is assignable to another type
    }

    private boolean hasIndexField() {
        return classNode.fields.stream().anyMatch(entry -> entry.name.equals("index"));
    }
}

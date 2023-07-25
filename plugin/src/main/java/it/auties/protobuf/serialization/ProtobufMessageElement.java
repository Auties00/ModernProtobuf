package it.auties.protobuf.serialization;

import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufType;
import org.objectweb.asm.Type;

import java.util.*;

class ProtobufMessageElement {
    private final String className;

    private final boolean enumType;
    private final List<ProtobufPropertyStub> properties;

    private final Map<String, Integer> constants;

    public ProtobufMessageElement(String className, boolean enumType) {
        this.className = className;
        this.properties = new ArrayList<>();
        this.constants = new TreeMap<>();
        this.enumType = enumType;
    }

    protected String className() {
        return className;
    }

    protected List<ProtobufPropertyStub> properties() {
        return Collections.unmodifiableList(properties);
    }

    protected boolean isEnum() {
        return enumType;
    }

    public Map<String, Integer> constants() {
        return Collections.unmodifiableMap(constants);
    }

    protected void addConstant(String fieldName, int fieldIndex) {
        constants.put(fieldName, fieldIndex);
    }

    protected void addProperty(Type fieldType, String fieldName, Map<String, Object> values) {
        var index = (int) values.get("index");
        var type = (ProtobufType) values.get("type");
        var required = (boolean) values.get("required");
        var rawImplementation = values.get("implementation");
        var implementation = getParsedImplementationType(type, fieldType, rawImplementation, required);
        var repeated = (boolean) values.get("repeated");
        var wrapperType = getWrapperType(fieldType, repeated);
        var ignore = (boolean) values.get("ignore");
        var packed = (boolean) values.get("packed");
        properties.add(new ProtobufPropertyStub(index, fieldName, type, implementation, wrapperType, required, ignore, repeated, packed));
    }

    private Type getWrapperType(Type javaType, boolean repeated) {
        return !repeated ? null : javaType;
    }

    private Type getParsedImplementationType(ProtobufType protoType, Type javaType, Object implementation, boolean required) {
        if(protoType != ProtobufType.MESSAGE && required) {
            return Type.getType(protoType.wrappedType());
        }

        var rawImplementation = castImplementationType(implementation);
        return rawImplementation == null || rawImplementation.getClassName().equals(ProtobufMessage.class.getName())
                ? javaType
                : rawImplementation;
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
}

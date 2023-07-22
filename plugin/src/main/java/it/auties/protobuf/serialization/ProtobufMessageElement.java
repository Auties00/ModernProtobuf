package it.auties.protobuf.serialization;

import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufProperty;
import it.auties.protobuf.base.ProtobufType;
import org.objectweb.asm.Type;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.TreeMap;

class ProtobufMessageElement {
    private final String className;

    private final boolean enumType;
    private final Map<String, ProtobufPropertyStub> fields;

    private final Map<String, Integer> constants;

    public ProtobufMessageElement(String className, boolean enumType) {
        this.className = className;
        this.fields = new TreeMap<>();
        this.constants = new TreeMap<>();
        this.enumType = enumType;
    }

    protected String className() {
        return className;
    }

    protected Map<String, ProtobufPropertyStub> fields() {
        return fields;
    }

    protected boolean isEnum() {
        return enumType;
    }

    public Map<String, Integer> constants() {
        return constants;
    }

    protected void addConstant(String fieldName, int fieldIndex) {
        constants.put(fieldName, fieldIndex);
    }

    protected void addField(String fieldType, String fieldName, Map<String, Object> values) {
        var index = (int) values.get("index");
        var type = (ProtobufType) values.get("type");
        Type implementation = getImplementation(fieldType, values);
        var required = (boolean) values.get("required");
        var ignore = (boolean) values.get("ignore");
        var repeated = (boolean) values.get("repeated");
        var packed = (boolean) values.get("packed");
        fields.put(fieldName, new ProtobufPropertyStub(index, type, implementation, required, ignore, repeated, packed));
    }

    private Type getImplementation(String fieldType, Map<String, Object> values) {
        var rawImplementation = getRawImplementation(values);
        return rawImplementation == null || rawImplementation.getClassName().equals(ProtobufMessage.class.getName())
                ? Type.getObjectType(fieldType)
                : rawImplementation;
    }

    private Type getRawImplementation(Map<String, Object> values) {
        var implementation = values.get("implementation");
        if(implementation instanceof Class<?> clazz) {
            return Type.getType(clazz);
        }else if(implementation instanceof Type type) {
            return type;
        }else {
            return null;
        }
    }
}

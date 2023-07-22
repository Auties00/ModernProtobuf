package it.auties.protobuf.serialization;

import it.auties.protobuf.base.ProtobufProperty;
import it.auties.protobuf.base.ProtobufType;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.TreeMap;

class ProtobufMessageElement {
    private final String className;

    private final boolean enumType;
    private final Map<String, ProtobufProperty> fields;

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

    protected Map<String, ProtobufProperty> fields() {
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

    protected void addField(String fieldName, Map<String, Object> values) {
        fields.put(fieldName, new ProtobufProperty(){
            @Override
            public Class<? extends Annotation> annotationType() {
                return ProtobufProperty.class;
            }

            @Override
            public int index() {
                return (int) values.get("index");
            }

            @Override
            public ProtobufType type() {
                return (ProtobufType) values.get("type");
            }

            @Override
            public Class<?> implementation() {
                return (Class<?>) values.get("implementation");
            }

            @Override
            public boolean required() {
                return (boolean) values.get("required");
            }

            @Override
            public boolean ignore() {
                return (boolean) values.get("ignore");
            }

            @Override
            public boolean repeated() {
                return (boolean) values.get("repeated");
            }

            @Override
            public boolean packed() {
                return (boolean) values.get("packed");
            }

            @Override
            public String toString() {
                return values.toString();
            }
        });
    }

    @Override
    public String toString() {
        return "ProtobufMessageElement{" +
                "className='" + className + '\'' +
                ", enumType=" + enumType +
                ", fields=" + fields +
                ", constants=" + constants +
                '}';
    }
}

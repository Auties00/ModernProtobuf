package it.auties.protobuf.serialization.model;

import it.auties.protobuf.model.ProtobufType;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.lang.reflect.Modifier;
import java.util.*;

import static java.lang.System.Logger.Level.WARNING;

public class ProtobufMessageElement {
    private final ClassNode classNode;
    private final List<ProtobufProperty> properties;

    private final Map<String, Integer> constants;

    public ProtobufMessageElement(ClassNode classNode) {
        this.classNode = classNode;
        this.properties = new ArrayList<>();
        this.constants = new TreeMap<>();
    }

    public String className() {
        return classNode.name;
    }

    public List<ProtobufProperty> properties() {
        return Collections.unmodifiableList(properties);
    }

    public boolean isEnum() {
        return (classNode.access & Opcodes.ACC_ENUM) != 0;
    }

    public Map<String, Integer> constants() {
        return Collections.unmodifiableMap(constants);
    }

    public void addConstant(String fieldName, int fieldIndex) {
        constants.put(fieldName, fieldIndex);
    }

    public boolean isProtobuf() {
        return !properties().isEmpty() || !constants().isEmpty();
    }

    public void addProperty(Type fieldType, String fieldName, Map<String, Object> values) {
        var index = (int) values.get("index");
        var type = (ProtobufType) values.get("type");
        var required = (boolean) values.get("required");
        var rawImplementation = values.get("implementation");
        var repeated = (boolean) values.get("repeated");
        var implementation = getParsedImplementationType(type, fieldType, rawImplementation, required, repeated);
        var wrapperType = getWrapperType(fieldType, repeated);
        var ignored = (boolean) values.get("ignored");
        if(ignored) {
            return;
        }

        var packed = (boolean) values.get("packed");
        properties.add(new ProtobufProperty(index, fieldName, type, implementation, wrapperType, required, repeated, packed));
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

    public void checkErrors() {
        if(isEnum()) {
            checkEnumIndexField();
            return;
        }

        checkPackedFields();
        checkRepeatedFieldsWrapper();
        // TODO: Can we check if a constructor for properties exists?
        // The problem tho is that it's kind of hard to determine if a type is assignable to another type
    }

    private void checkPackedFields() {
        properties.stream()
                .filter(ProtobufProperty::packed)
                .forEach(this::checkPackedField);
    }

    private void checkPackedField(ProtobufProperty entry) {
        if(entry.repeated()){
            return;
        }

        throw new IllegalArgumentException("%s is not repeated: only repeated fields can be marked as packed".formatted(entry.name()));
    }

    private void checkRepeatedFieldsWrapper() {
        properties.stream()
                .filter(ProtobufProperty::repeated)
                .forEach(this::checkRepeatedFieldWrapper);
    }

    private void checkRepeatedFieldWrapper(ProtobufProperty property) {
        var genericType = property.wrapperType().getClassName();
        try {
            var rawType = genericType.substring(0, genericType.indexOf("<"));
            var javaClass = Class.forName(rawType);
            if (Modifier.isAbstract(javaClass.getModifiers())) {
                throw new IllegalArgumentException("%s %s is abstract: this is not allowed for repeated types!".formatted(genericType, property.name()));
            }

            if (javaClass.isAssignableFrom(Collection.class)) {
                throw new IllegalArgumentException("%s %s is not a collection: this is not allowed for repeated types!".formatted(genericType, property.name()));
            }

            try {
                javaClass.getConstructor();
            }catch (NoSuchMethodException ignored) {
                throw new IllegalArgumentException("%s doesn't provide a no-args constructor: this is not allowed for repeated types!".formatted(genericType));
            }
        } catch (ClassNotFoundException exception) {
            var logger = System.getLogger("Protobuf");
            logger.log(WARNING, "Cannot check whether %s is a valid type for a repeated field as it's not part of the std Java library".formatted(genericType));
        }
    }

    private void checkEnumIndexField() {
        if(classNode.fields.stream().anyMatch(entry -> entry.name.equals("index"))){
            return;
        }

        throw new IllegalArgumentException("Missing index field in enum " + className());
    }
}

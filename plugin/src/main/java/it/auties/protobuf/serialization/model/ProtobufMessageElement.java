package it.auties.protobuf.serialization.model;

import it.auties.protobuf.model.ProtobufType;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.System.Logger.Level.WARNING;

public class ProtobufMessageElement {
    private final ClassNode classNode;
    private final ClassReader classReader;
    private final Map<Integer, ProtobufProperty> properties;
    private final Map<Integer, String> constants;

    public ProtobufMessageElement(ClassNode classNode, ClassReader classReader) {
        this.classNode = classNode;
        this.classReader = classReader;
        this.properties = new LinkedHashMap<>();
        this.constants = new LinkedHashMap<>();
    }

    public String className() {
        return classNode.name;
    }

    public List<ProtobufProperty> properties() {
        return List.copyOf(properties.values());
    }

    public boolean isEnum() {
        return (classNode.access & Opcodes.ACC_ENUM) != 0;
    }

    public Map<Integer, String> constants() {
        return Collections.unmodifiableMap(constants);
    }

    public ClassReader classReader() {
        return classReader;
    }

    public void addConstant(int fieldIndex, String fieldName) {
        var error = constants.put(fieldIndex, fieldName);
        if(error == null) {
            return;
        }

        throw new IllegalArgumentException("Duplicated protobuf constant with index %s: %s/%s in %s".formatted(fieldIndex, fieldName, error, className()));
    }

    public boolean isProtobuf() {
        return !properties().isEmpty() || !constants().isEmpty();
    }

    public Optional<ProtobufProperty> addProperty(String fieldName, String fieldDescription, String fieldSignature, Map<String, Object> values) {
        var index = (int) values.get("index");
        var type = (ProtobufType) values.get("type");
        var required = (boolean) values.get("required");
        var rawImplementation = values.get("implementation");
        var repeated = (boolean) values.get("repeated");
        var implementation = getParsedImplementationType(type, Type.getType(Objects.requireNonNullElse(fieldSignature, fieldDescription)), rawImplementation, required, repeated);
        var wrapperType = getWrapperType(fieldDescription, repeated);
        var ignored = (boolean) values.get("ignored");
        if(ignored) {
            return Optional.empty();
        }

        var packed = (boolean) values.get("packed");
        var result = new ProtobufProperty(index, fieldName, type, implementation, new AtomicReference<>(), wrapperType, required, repeated, packed);
        var error = properties.put(index, result);
        if (error != null) {
            throw new IllegalArgumentException("Duplicate protobuf field with index %s: %s/%s in %s".formatted(index, fieldName, error.name(), className()));
        }

        return Optional.of(result);
    }

    private static Type getWrapperType(String fieldDescription, boolean repeated) {
        if (!repeated) {
            return null;
        }

        return Type.getType(fieldDescription);
    }

    private Type getParsedImplementationType(ProtobufType protoType, Type javaType, Object implementation, boolean required, boolean repeated) {
        if(protoType != ProtobufType.MESSAGE && protoType != ProtobufType.ENUM && required) {
            return Type.getType(protoType.wrappedType());
        }

        var rawImplementation = castImplementationType(implementation);
        if (rawImplementation != null && !rawImplementation.getClassName().equals(Object.class.getName())) {
            return rawImplementation;
        }

        if (!repeated) {
            return javaType;
        }

        var javaTypeName = javaType.getInternalName();
        var paramsStart = javaTypeName.indexOf("<");
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
        properties.values()
                .stream()
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
        properties.values()
                .stream()
                .filter(ProtobufProperty::repeated)
                .forEach(this::checkRepeatedFieldWrapper);
    }

    private void checkRepeatedFieldWrapper(ProtobufProperty property) {
        var wrapperTypeName = property.wrapperType().getClassName();
        try {
            var javaClass = Class.forName(wrapperTypeName);
            if (Modifier.isAbstract(javaClass.getModifiers())) {
                throw new IllegalArgumentException("%s %s is abstract: this is not allowed for repeated types!".formatted(wrapperTypeName, property.name()));
            }

            if (javaClass.isAssignableFrom(Collection.class)) {
                throw new IllegalArgumentException("%s %s is not a collection: this is not allowed for repeated types!".formatted(wrapperTypeName, property.name()));
            }

            try {
                javaClass.getConstructor();
            }catch (NoSuchMethodException ignored) {
                throw new IllegalArgumentException("%s doesn't provide a no-args constructor: this is not allowed for repeated types!".formatted(wrapperTypeName));
            }
        } catch (ClassNotFoundException exception) {
            var logger = System.getLogger("Protobuf");
            logger.log(WARNING, "Cannot check whether %s is a valid type for a repeated field as it's not part of the std Java library".formatted(wrapperTypeName));
        }
    }

    private void checkEnumIndexField() {
        if(classNode.fields.stream().anyMatch(entry -> entry.name.equals("index"))){
            return;
        }

        throw new IllegalArgumentException("Missing index field in enum " + className());
    }

    @Override
    public String toString() {
        return className();
    }
}

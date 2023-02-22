package it.auties.protobuf.serialization.jackson;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.auties.protobuf.base.ProtobufConverter;
import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufProperty;
import it.auties.protobuf.base.ProtobufType;
import it.auties.protobuf.serialization.exception.ProtobufSerializationException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Objects;

record ProtobufField(Object value, Class<? extends ProtobufMessage> messageType, int index,
                     ProtobufType type, Class<?> implementation, String name, boolean required,
                     boolean ignored, boolean packed, boolean repeated, boolean convert) {
    public static boolean isProperty(Field field) {
        var annotation = field.getAnnotation(ProtobufProperty.class);
        return annotation != null && !annotation.ignore();
    }

    public static ProtobufField ofWrite(Object owner, Field field) {
        var protobufProperty = field.getAnnotation(ProtobufProperty.class);
        var jsonProperty = field.getAnnotation(JsonProperty.class);
        return new ProtobufField(
            getValue(owner, field),
            null,
            protobufProperty.index(),
            protobufProperty.type(),
            protobufProperty.implementation(),
            jsonProperty != null ? jsonProperty.value() : field.getName(),
            protobufProperty.required(),
            protobufProperty.ignore(),
            protobufProperty.packed(),
            protobufProperty.repeated(),
            false
        );
    }

    private static Object getValue(Object owner, Field field) {
        try {
            field.setAccessible(true);
            var value = field.get(owner);
            var converter = Arrays.stream(field.getType().getDeclaredMethods())
                    .filter(entry -> entry.getAnnotation(ProtobufConverter.class) != null && !Modifier.isStatic(entry.getModifiers()))
                    .findFirst()
                    .orElse(null);
            if (converter != null) {
                converter.setAccessible(true);
                return converter.invoke(value);
            }
            return value;
        } catch (ReflectiveOperationException exception) {
            throw new ProtobufSerializationException("Cannot access field %s inside class %s".formatted(field.getName(), field.getDeclaringClass().getName()), exception);
        }
    }

    public static ProtobufField ofRead(Field field) {
        var protobufProperty = field.getAnnotation(ProtobufProperty.class);
        var jsonProperty = field.getAnnotation(JsonProperty.class);
        return new ProtobufField(
            null,
            getJavaType(field, protobufProperty),
            protobufProperty.index(),
            protobufProperty.type(),
            protobufProperty.implementation(),
            jsonProperty != null ? jsonProperty.value() : field.getName(),
            protobufProperty.required(),
            protobufProperty.ignore(),
            protobufProperty.packed(),
            protobufProperty.repeated(),
            requiresConversion(field, protobufProperty)
        );
    }

    private static Class<? extends ProtobufMessage> getJavaType(Field field,
        ProtobufProperty property) {
        if (property.implementation() != null
            && property.implementation() != ProtobufMessage.class
            && ProtobufMessage.isMessage(property.implementation())) {
            return property.implementation()
                .asSubclass(ProtobufMessage.class);
        }
        if (ProtobufMessage.isMessage(field.getType())) {
            return field.getType()
                .asSubclass(ProtobufMessage.class);
        }
        return null;
    }

    private static boolean requiresConversion(Field field, ProtobufProperty property) {
        if (property.repeated()) {
            return false;
        }
        if (property.type() != ProtobufType.MESSAGE) {
            return !property.type().isAssignableFrom(field.getType());
        }
        return property.implementation() != ProtobufMessage.class
            && property.implementation() != field.getType();
    }

    @SuppressWarnings("unchecked")
    public <T> T toValue() {
        try {
            return (T) value;
        } catch (ClassCastException exception) {
            throw new RuntimeException(
                "A field misreported its own type in a schema: %s".formatted(this), exception);
        }
    }

    public boolean isValid() {
        if (required && value == null) {
            throw new ProtobufSerializationException(
                "Erroneous field at index %s with type %s(%s): missing mandatory value"
                    .formatted(index, type, Objects.toString(messageType)));
        }
        return value != null;
    }

    public ProtobufField toSingleField(Object value) {
        return new ProtobufField(
            value,
            messageType,
            index,
            type,
            implementation,
            name,
            required,
            ignored,
            packed,
            false,
            convert
        );
    }

    @Override
    public String toString() {
        return "ProtobufField{" +
            "value=" + value +
            ", messageType=" + messageType +
            ", metadata=" +
            "{index=" + index +
            ", implementation=" + implementation.getName() +
            ", name=" + name +
            ", required=" + required +
            ", ignore=" + ignored +
            ", packed=" + packed +
            ", repeated=" + repeated +
            "}" +
            ", convert=" + convert +
            '}';
    }
}
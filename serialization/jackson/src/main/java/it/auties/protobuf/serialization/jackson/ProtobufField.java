package it.auties.protobuf.serialization.jackson;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufProperty;
import it.auties.protobuf.base.ProtobufType;
import it.auties.protobuf.serialization.exception.ProtobufSerializationException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.experimental.Delegate;
import lombok.experimental.FieldDefaults;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Objects;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@FieldDefaults(makeFinal=true, level= AccessLevel.PRIVATE)
@Accessors(fluent = true)
class ProtobufField {
    @Getter
    Object value;

    @Getter
    Class<? extends ProtobufMessage> messageType;

    @Delegate
    ProtobufProperty metadata;

    @Getter
    boolean convert;

    public static boolean isProperty(Field field) {
        var annotation = field.getAnnotation(ProtobufProperty.class);
        return annotation != null && !annotation.ignore();
    }

    public static ProtobufField ofWrite(Object owner, Field field) {
        var protobufProperty =  field.getAnnotation(ProtobufProperty.class);
        var jsonProperty = field.getAnnotation(JsonProperty.class);
        return new ProtobufField(
                getValue(owner, field),
                null,
                createPlaceholderAnnotation(
                        jsonProperty != null ? jsonProperty.value() : field.getName(),
                        protobufProperty.repeated(),
                        protobufProperty
                ),
                false
        );
    }

    private static Object getValue(Object owner, Field field) {
        try {
            field.setAccessible(true);
            var value = field.get(owner);
            return value instanceof ProtobufMessage message && message.isValueBased()
                    ? message.toValue() : value;
        } catch (ReflectiveOperationException exception) {
            throw new ProtobufSerializationException("Cannot access field %s inside class %s"
                    .formatted(field.getName(), field.getDeclaringClass().getName()), exception);
        }
    }

    public static ProtobufField ofRead(Field field) {
        var protobufProperty =  field.getAnnotation(ProtobufProperty.class);
        var jsonProperty = field.getAnnotation(JsonProperty.class);
        return new ProtobufField(
                null,
                getJavaType(field, protobufProperty),
                createPlaceholderAnnotation(
                        jsonProperty != null ? jsonProperty.value() : field.getName(),
                        protobufProperty.repeated(),
                        protobufProperty
                ),
                requiresConversion(field, protobufProperty)
        );
    }

    private static Class<? extends ProtobufMessage> getJavaType(Field field, ProtobufProperty property) {
        if (property.implementation() != null
                && property.implementation() != ProtobufMessage.class
                && ProtobufMessage.isMessage(property.implementation())) {
            return property.implementation()
                    .asSubclass(ProtobufMessage.class);
        }

        if(ProtobufMessage.isMessage(field.getType())){
            return field.getType()
                    .asSubclass(ProtobufMessage.class);
        }

        return null;
    }

    private static boolean requiresConversion(Field field, ProtobufProperty property) {
        if(property.type() != ProtobufType.MESSAGE){
            return !property.type().wrappedType().isAssignableFrom(field.getType())
                    && !property.type().primitiveType().isAssignableFrom(field.getType());
        }

        return property.implementation() != ProtobufMessage.class
                && property.implementation() != field.getType()
                && !property.repeated();
    }
    
    @SuppressWarnings("unchecked")
    public <T> T toValue() {
        try {
            return (T) value;
        } catch (ClassCastException exception) {
            throw new RuntimeException("A field misreported its own type in a schema: %s".formatted(this), exception);
        }
    }

    public boolean isValid() {
        if (metadata.required() && value == null) {
            throw new ProtobufSerializationException("Erroneous field at index %s with type %s(%s): missing mandatory value"
                    .formatted(metadata.index(), metadata.type(), Objects.toString(messageType)));
        }

        return value != null;
    }

    public ProtobufField toSingleField(Object value) {
        return new ProtobufField(
                value,
                messageType,
                createPlaceholderAnnotation(
                        name(),
                        false,
                        metadata
                ),
                convert
        );
    }

    private static ProtobufProperty createPlaceholderAnnotation(String name, boolean repeated, ProtobufProperty metadata) {
        return new ProtobufProperty() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return ProtobufProperty.class;
            }

            @Override
            public int index() {
                return metadata.index();
            }

            @Override
            public ProtobufType type() {
                return metadata.type();
            }

            @Override
            public Class<?> implementation() {
                return metadata.implementation();
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public boolean required() {
                return metadata.required();
            }

            @Override
            public boolean ignore() {
                return metadata.ignore();
            }

            @Override
            public boolean packed() {
                return metadata.packed();
            }

            @Override
            public boolean repeated() {
                return repeated;
            }
        };
    }
}
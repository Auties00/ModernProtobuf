package it.auties.protobuf.api.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.auties.protobuf.api.exception.ProtobufSerializationException;
import it.auties.protobuf.api.model.ProtobufMessage;
import it.auties.protobuf.api.model.ProtobufProperty;
import it.auties.protobuf.api.model.ProtobufValue;
import lombok.experimental.UtilityClass;

import java.lang.reflect.Field;

@UtilityClass
public class ProtobufUtils {
    public String getFieldName(Field field) {
        var notation = field.getAnnotation(JsonProperty.class);
        return notation == null ? field.getName() : notation.value();
    }

    public boolean isProperty(Field field) {
        var annotation = field.getAnnotation(ProtobufProperty.class);
        return annotation != null && !annotation.ignore();
    }

    public ProtobufProperty getProperty(Field field) {
        var property = field.getAnnotation(ProtobufProperty.class);
        return property == null || property.ignore() ? null : property;
    }

    public Class<? extends ProtobufMessage> getJavaType(ProtobufProperty property) {
        if (property.concreteType() == Object.class) {
            return null;
        }

        if (property.concreteType() == null) {
            throw new ProtobufSerializationException("Missing concrete type property type");
        }

        if (property.concreteType().isEnum()) {
            return null;
        }

        if (!ProtobufMessage.isMessage(property.concreteType())) {
            throw new ProtobufSerializationException("%s is not a valid message type. ".formatted(property.concreteType()) +
                    "This usually means that there is a missing concrete type property or that said class is not a ProtobufMessage");
        }

        return property.concreteType()
                .asSubclass(ProtobufMessage.class);
    }

    public boolean hasValue(Class<?> type) {
        return type.isAnnotationPresent(ProtobufValue.class);
    }
}

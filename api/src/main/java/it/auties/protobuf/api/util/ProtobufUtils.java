package it.auties.protobuf.api.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.auties.protobuf.api.exception.ProtobufSerializationException;
import it.auties.protobuf.api.model.ProtobufMessage;
import it.auties.protobuf.api.model.ProtobufProperty;
import lombok.experimental.UtilityClass;

import java.lang.reflect.Field;

import static it.auties.protobuf.api.model.ProtobufProperty.Type.MESSAGE;

@UtilityClass
public class ProtobufUtils {
    // Needed to fix MixedNotationsTest bug
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

    public Class<? extends ProtobufMessage> getJavaType(Field field, ProtobufProperty property) {
        if (property.type() != MESSAGE) {
            return null;
        }

        if (property.implementation() != null && property.implementation() != ProtobufMessage.class) {
            return property.implementation();
        }

        if(!ProtobufMessage.isMessage(field.getType())){
            throw new ProtobufSerializationException("Field %s inside class %s with type %s doesn't implement ProtobufMessage"
                    .formatted(field.getName(), field.getDeclaringClass().getName(), field.getType().getName()));
        }

        return field.getType().asSubclass(ProtobufMessage.class);
    }
}

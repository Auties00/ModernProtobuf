package it.auties.protobuf.serializer.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufProperty;
import lombok.experimental.UtilityClass;

import java.lang.reflect.Field;

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
        if (property.implementation() != null && property.implementation() != ProtobufMessage.class && ProtobufMessage.isMessage(property.implementation())) {
            return property.implementation()
                    .asSubclass(ProtobufMessage.class);
        }

        if(ProtobufMessage.isMessage(field.getType())){
            return field.getType()
                    .asSubclass(ProtobufMessage.class);
        }

        return null;
    }
}

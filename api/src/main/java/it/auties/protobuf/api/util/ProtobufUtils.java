package it.auties.protobuf.api.util;

import it.auties.protobuf.api.model.ProtobufMessage;
import it.auties.protobuf.api.model.ProtobufProperty;
import it.auties.protobuf.api.model.ProtobufProperty.Type;
import it.auties.protobuf.api.model.ProtobufValue;
import lombok.experimental.UtilityClass;

import java.lang.reflect.Field;
import java.util.Optional;

@UtilityClass
public class ProtobufUtils {
    public boolean isProperty(Field field){
        var annotation = field.getAnnotation(ProtobufProperty.class);
        return annotation != null && !annotation.ignore();
    }

    public ProtobufProperty getProperty(Field field){
        var property = field.getAnnotation(ProtobufProperty.class);
        return property == null || property.ignore() ? null : property;
    }

    public Class<?> getJavaType(ProtobufProperty property) {
        return property.concreteType() == Object.class ? property.type().javaType() :
                property.concreteType();
    }

    public Class<? extends ProtobufMessage> getMessageType(Class<?> clazz){
        return !ProtobufMessage.isMessage(clazz) ? null
                :clazz.asSubclass(ProtobufMessage.class);
    }

    public boolean hasValue(Class<?> type) {
        return type.isAnnotationPresent(ProtobufValue.class);
    }
}

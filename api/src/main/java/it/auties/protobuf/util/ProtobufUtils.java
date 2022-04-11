package it.auties.protobuf.util;

import it.auties.protobuf.exception.ProtobufException;
import it.auties.protobuf.model.ProtobufMessage;
import it.auties.protobuf.model.ProtobufProperty;
import it.auties.protobuf.model.ProtobufProperty.Type;
import it.auties.protobuf.model.ProtobufValue;
import lombok.experimental.UtilityClass;

import java.lang.reflect.Field;
import java.util.Optional;

@UtilityClass
public class ProtobufUtils {
    public boolean isProperty(Field field){
        var annotation = field.getAnnotation(ProtobufProperty.class);
        return annotation != null && !annotation.ignore();
    }

    public Optional<ProtobufProperty> getProperty(Field field){
        return Optional.ofNullable(field.getAnnotation(ProtobufProperty.class))
                .filter(annotation -> !annotation.ignore());
    }

    public Type getProtobufType(ProtobufProperty property){
        return property.concreteType() == Object.class ? property.type()
                : Type.forJavaType(property.concreteType());
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

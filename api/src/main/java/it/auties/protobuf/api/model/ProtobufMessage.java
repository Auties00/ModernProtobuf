package it.auties.protobuf.api.model;

import it.auties.protobuf.api.model.ProtobufProperty.Type;

import java.util.Objects;

@SuppressWarnings("BooleanMethodIsAlwaysInverted")
public interface ProtobufMessage {
    static boolean isMessage(Class<?> clazz){
        return clazz != null && ProtobufMessage.class.isAssignableFrom(clazz);
    }

    default Object value(){
        return null;
    }
}

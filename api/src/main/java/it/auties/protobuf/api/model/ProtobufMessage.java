package it.auties.protobuf.api.model;

@SuppressWarnings("BooleanMethodIsAlwaysInverted")
public interface ProtobufMessage {
    static boolean isMessage(Class<?> clazz){
        return clazz != null && ProtobufMessage.class.isAssignableFrom(clazz);
    }

    default Object value(){
        return null;
    }
}

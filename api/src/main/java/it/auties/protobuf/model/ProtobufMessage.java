package it.auties.protobuf.model;

public interface ProtobufMessage {
    static boolean isMessage(Class<?> clazz){
        return clazz != null && ProtobufMessage.class.isAssignableFrom(clazz);
    }

    default Object value(){
        return null;
    }
}

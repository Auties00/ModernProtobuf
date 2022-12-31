package it.auties.protobuf.base;

public interface ProtobufMessage {
    static boolean isMessage(Class<?> clazz) {
        return clazz != null
                && ProtobufMessage.class.isAssignableFrom(clazz);
    }

    default boolean isValueBased(){
        return false;
    }

    default Object toValue() {
        return null;
    }
}

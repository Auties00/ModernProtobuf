package it.auties.protobuf.base;

@SuppressWarnings("unused")
public interface ProtobufMessage {
    static boolean isMessage(Class<?> clazz) {
        return clazz != null
                && ProtobufMessage.class.isAssignableFrom(clazz);
    }
}

package it.auties.protobuf.base;

@SuppressWarnings("unused")
public interface ProtobufMessage {
    String SERIALIZATION_METHOD = "toEncodedProtobuf";
    String DESERIALIZATION_CLASS_METHOD = "ofProtobuf";
    String DESERIALIZATION_ENUM_METHOD = "of";

    static boolean isMessage(Class<?> clazz) {
        return clazz != null
                && ProtobufMessage.class.isAssignableFrom(clazz);
    }
}

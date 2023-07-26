package it.auties.protobuf;

import it.auties.protobuf.exception.ProtobufDeserializationException;
import it.auties.protobuf.exception.ProtobufSerializationException;

public class Protobuf {
    public static final String SERIALIZATION_METHOD = "toEncodedProtobuf";
    public static final String DESERIALIZATION_CLASS_METHOD = "ofProtobuf";
    public static final String DESERIALIZATION_ENUM_METHOD = "of";
    
    @SuppressWarnings("unchecked")
    public static <T> T readMessage(byte[] message, Class<T> clazz) {
        try {
            var method = clazz.getMethod(DESERIALIZATION_CLASS_METHOD, byte[].class);
            return (T) method.invoke(null, new Object[]{message});
        }catch (Throwable exception){
            throw new ProtobufDeserializationException(exception);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T readMessage(int index, Class<T> clazz) {
        try {
            var method = clazz.getMethod(DESERIALIZATION_ENUM_METHOD, int.class);
            return (T) method.invoke(null, new Object[]{index});
        }catch (Throwable exception){
            throw new ProtobufDeserializationException(exception);
        }
    }

    public static byte[] writeMessage(Object object) {
        try {
            var method = object.getClass().getMethod(SERIALIZATION_METHOD);
            return (byte[]) method.invoke(object);
        }catch (Throwable exception){
            throw new ProtobufSerializationException(exception);
        }
    }
}

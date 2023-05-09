package it.auties.protobuf;

import it.auties.protobuf.base.ProtobufDeserializationException;
import it.auties.protobuf.base.ProtobufSerializationException;

import java.io.IOException;

public interface Protobuf {
    @SuppressWarnings("unchecked")
    default <T> T readMessage(byte[] message, Class<T> clazz) throws IOException {
        try {
            var method = clazz.getMethod("ofProtobuf", byte[].class);
            return (T) method.invoke(null, new Object[]{message});
        }catch (Throwable exception){
            throw new ProtobufDeserializationException(exception);
        }
    }

    default <T> byte[] writeValueAsBytes(T object) {
        try {
            var method = object.getClass().getMethod("toEncodedProtobuf");
            return (byte[]) method.invoke(object);
        }catch (Throwable exception){
            throw new ProtobufSerializationException(exception);
        }
    }
}

package it.auties.protobuf;

import it.auties.protobuf.base.ProtobufDeserializationException;
import it.auties.protobuf.base.ProtobufSerializationException;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public interface TestProvider {
    @SuppressWarnings("unchecked")
    default <T> T readMessage(byte[] message, Class<T> clazz) throws IOException {
        try {
            var method = clazz.getMethod("of", byte[].class);
            return (T) method.invoke(null, new Object[]{message});
        }catch (Throwable exception){
            throw new ProtobufDeserializationException(exception);
        }
    }

    default <T> byte[] writeValueAsBytes(T object) throws IOException {
        try {
            var method = object.getClass().getMethod("toByteArray");
            return (byte[]) method.invoke(object);
        }catch (Throwable exception){
            throw new ProtobufSerializationException(exception);
        }
    }
}

package it.auties.protobuf.decoder;

import java.util.Map;

public interface ProtobufTypeDescriptor {
    Map<Integer, Class<?>> descriptor();

    static boolean hasDescriptor(Class<?> clazz){
        return ProtobufTypeDescriptor.class.isAssignableFrom(clazz);
    }
}

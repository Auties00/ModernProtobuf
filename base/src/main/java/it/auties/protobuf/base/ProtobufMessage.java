package it.auties.protobuf.base;

import java.util.List;

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

    @SuppressWarnings("unused") // It will be used by the decoder
    default List<String> reservedFieldNames(){
        return List.of();
    }

    @SuppressWarnings("unused") // It will be used by the decoder
    default List<Integer> reservedFieldIndexes(){
        return List.of();
    }
}

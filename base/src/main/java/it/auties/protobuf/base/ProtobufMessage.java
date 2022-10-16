package it.auties.protobuf.base;

import java.util.List;

public interface ProtobufMessage {
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    static boolean isMessage(Class<?> clazz) {
        return clazz != null && ProtobufMessage.class.isAssignableFrom(clazz);
    }

    default boolean isValueBased(){
        return false;
    }

    default Object toValue() {
        return null;
    }

    default List<String> reservedFieldNames(){
        return null;
    }

    default List<Integer> reservedFieldIndexes(){
        return null;
    }
}

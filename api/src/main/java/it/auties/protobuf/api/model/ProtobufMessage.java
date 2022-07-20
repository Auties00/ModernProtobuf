package it.auties.protobuf.api.model;

import java.util.List;

@SuppressWarnings("BooleanMethodIsAlwaysInverted")
public interface ProtobufMessage {
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

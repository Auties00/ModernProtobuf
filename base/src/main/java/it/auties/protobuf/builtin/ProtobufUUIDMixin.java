package it.auties.protobuf.builtin;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufMixin;
import it.auties.protobuf.annotation.ProtobufSerializer;
import it.auties.protobuf.model.ProtobufLazyString;

import java.util.UUID;

@SuppressWarnings("unused")
@ProtobufMixin
public final class ProtobufUUIDMixin {
    @ProtobufDeserializer
    public static UUID ofNullable(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    @ProtobufSerializer
    public static String toValue(UUID value) {
        return value == null ? null : value.toString();
    }
}

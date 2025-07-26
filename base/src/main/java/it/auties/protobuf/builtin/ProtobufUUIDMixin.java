package it.auties.protobuf.builtin;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufMixin;
import it.auties.protobuf.annotation.ProtobufSerializer;
import it.auties.protobuf.model.ProtobufString;

import java.util.UUID;

@SuppressWarnings("unused")
@ProtobufMixin
public final class ProtobufUUIDMixin {
    @ProtobufDeserializer
    public static UUID ofNullable(ProtobufString value) {
        return value == null ? null : UUID.fromString(value.toString());
    }

    @ProtobufSerializer
    public static ProtobufString toValue(UUID value) {
        return value == null ? null : ProtobufString.wrap(value.toString());
    }
}

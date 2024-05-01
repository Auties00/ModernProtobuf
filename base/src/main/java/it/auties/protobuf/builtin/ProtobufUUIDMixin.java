package it.auties.protobuf.builtin;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufSerializer;
import it.auties.protobuf.model.ProtobufMixin;

import java.util.UUID;

import static it.auties.protobuf.annotation.ProtobufDeserializer.BuilderBehaviour.ADD;

@SuppressWarnings("unused")
public class ProtobufUUIDMixin implements ProtobufMixin {
    @ProtobufDeserializer(builderBehaviour = ADD)
    public static UUID ofNullable(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    @ProtobufSerializer
    public static String toValue(UUID value) {
        return value == null ? null : value.toString();
    }
}

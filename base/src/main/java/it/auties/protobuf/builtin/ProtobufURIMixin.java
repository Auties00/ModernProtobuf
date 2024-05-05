package it.auties.protobuf.builtin;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufSerializer;

import java.net.URI;
import java.util.UUID;

import static it.auties.protobuf.annotation.ProtobufDeserializer.BuilderBehaviour.ADD;

@SuppressWarnings("unused")
public class ProtobufURIMixin {
    @ProtobufDeserializer(builderBehaviour = ADD)
    public static URI ofNullable(String value) {
        return value == null ? null : URI.create(value);
    }

    @ProtobufSerializer
    public static String toValue(UUID value) {
        return value == null ? null : value.toString();
    }
}

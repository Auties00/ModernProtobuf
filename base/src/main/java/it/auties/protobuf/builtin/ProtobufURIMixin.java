package it.auties.protobuf.builtin;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufMixin;
import it.auties.protobuf.annotation.ProtobufSerializer;
import it.auties.protobuf.model.ProtobufString;

import java.net.URI;

@SuppressWarnings("unused")
@ProtobufMixin
public final class ProtobufURIMixin {
    @ProtobufDeserializer
    public static URI ofNullable(ProtobufString value) {
        return value == null ? null : URI.create(value.toString());
    }

    @ProtobufSerializer
    public static ProtobufString toValue(URI value) {
        return value == null ? null : ProtobufString.wrap(value.toString());
    }
}

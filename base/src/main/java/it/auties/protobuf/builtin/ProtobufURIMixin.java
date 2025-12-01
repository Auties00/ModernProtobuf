package it.auties.protobuf.builtin;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufMixin;
import it.auties.protobuf.annotation.ProtobufSerializer;
import it.auties.protobuf.model.ProtobufLazyString;

import java.net.URI;

@SuppressWarnings("unused")
@ProtobufMixin
public final class ProtobufURIMixin {
    @ProtobufDeserializer
    public static URI ofNullable(String value) {
        return value == null ? null : URI.create(value);
    }

    @ProtobufSerializer
    public static String toValue(URI value) {
        return value == null ? null : value.toString();
    }
}

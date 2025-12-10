package it.auties.protobuf.builtin;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufMixin;
import it.auties.protobuf.annotation.ProtobufSerializer;
import it.auties.protobuf.exception.ProtobufDeserializationException;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

@SuppressWarnings("unused")
@ProtobufMixin
public final class URLMixin {
    @ProtobufDeserializer
    public static URL ofNullable(String value) {
        try {
            return value == null ? null : URI.create(value).toURL();
        }catch (MalformedURLException exception) {
            throw new ProtobufDeserializationException("Cannot deserialize URL", exception);
        }
    }

    @ProtobufSerializer
    public static String toValue(URL value) {
        return value == null ? null : value.toString();
    }
}

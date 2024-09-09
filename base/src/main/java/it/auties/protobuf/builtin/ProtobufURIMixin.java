package it.auties.protobuf.builtin;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufMixin;
import it.auties.protobuf.annotation.ProtobufSerializer;
import it.auties.protobuf.model.ProtobufString;

import java.net.URI;

import static it.auties.protobuf.annotation.ProtobufDeserializer.BuilderBehaviour.ADD;

@SuppressWarnings("unused")
@ProtobufMixin
public class ProtobufURIMixin {
    @ProtobufDeserializer(builderBehaviour = ADD)
    public static URI ofNullable(ProtobufString value) {
        return value == null ? null : URI.create(value.toString());
    }

    @ProtobufSerializer
    public static ProtobufString toValue(URI value) {
        return value == null ? null : ProtobufString.wrap(value.toString());
    }
}

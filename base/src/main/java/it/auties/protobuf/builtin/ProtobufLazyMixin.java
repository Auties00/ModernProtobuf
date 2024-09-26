package it.auties.protobuf.builtin;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufMixin;
import it.auties.protobuf.annotation.ProtobufSerializer;
import it.auties.protobuf.model.ProtobufString;

import java.nio.ByteBuffer;

import static it.auties.protobuf.annotation.ProtobufDeserializer.BuilderBehaviour.ADD;

@SuppressWarnings("unused")
@ProtobufMixin
public class ProtobufLazyMixin {
    @ProtobufDeserializer(builderBehaviour = ADD)
    public static String ofNullable(ProtobufString value) {
        return value == null ? null : value.toString();
    }

    @ProtobufSerializer
    public static ProtobufString toValue(String value) {
        return value == null ? null : ProtobufString.wrap(value);
    }

    @ProtobufDeserializer(builderBehaviour = ADD)
    public static byte[] ofNullable(ByteBuffer value) {
        if(value == null) {
            return null;
        }

        var result = new byte[value.remaining()];
        value.get(result);
        return result;
    }

    @ProtobufSerializer
    public static ByteBuffer toValue(byte[] value) {
        return value == null ? null : ByteBuffer.wrap(value);
    }
}

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
    @ProtobufDeserializer(
            warning = "Possible implicit UTF-8 decoding from ProtobufString source to String",
            builderBehaviour = ADD
    )
    public static String ofAnyString(ProtobufString value) {
        return value == null ? null : value.toString();
    }

    @ProtobufSerializer
    public static ProtobufString toValue(String value) {
        return value == null ? null : ProtobufString.wrap(value);
    }

    @ProtobufDeserializer(
            warning = "Implicit UTF-8 decoding from ProtobufString.Lazy source to ProtobufString.Value",
            builderBehaviour = ADD
    )
    public static ProtobufString.Value ofLazyString(ProtobufString.Lazy value) {
        if(value == null) {
            return null;
        }

        var string = new String(value.encodedBytes(), value.encodedOffset(), value.encodedLength());
        return ProtobufString.wrap(string);
    }

    @ProtobufDeserializer(
            warning = "Implicit memory copy from ByteBuffer to byte[]",
            builderBehaviour = ADD
    )
    public static byte[] ofBuffer(ByteBuffer value) {
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

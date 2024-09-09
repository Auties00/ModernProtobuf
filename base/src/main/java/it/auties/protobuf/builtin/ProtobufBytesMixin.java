package it.auties.protobuf.builtin;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufMixin;

import java.nio.ByteBuffer;

@ProtobufMixin
public class ProtobufBytesMixin {
    @ProtobufDeserializer
    public static byte[] toBytes(ByteBuffer buffer) {
        var bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }
}

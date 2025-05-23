package it.auties.proto.compatibility.scalar;

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public class GoogleProtocTest {
    @Test
        public void encodeScalarTypes() {
        var random = new Random();
        var googleMessage = GoogleScalarMessage.newBuilder()
                .setString("Hello, this is an automated test")
                .setBytes(ByteString.copyFrom("Hello, this is an automated test".getBytes(StandardCharsets.UTF_8)))
                .setFixed32(random.nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE))
                .setSfixed32(random.nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE))
                .setInt32(random.nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE))
                .setUint32(random.nextInt(0, Integer.MAX_VALUE))
                .setFixed64(random.nextLong(Long.MIN_VALUE, Long.MAX_VALUE))
                .setSfixed64(random.nextLong(Long.MIN_VALUE, Long.MAX_VALUE))
                .setBool(random.nextBoolean())
                .setDouble(random.nextDouble(Double.MIN_VALUE, Double.MAX_VALUE))
                .setFloat(random.nextFloat(Float.MIN_VALUE, Float.MAX_VALUE))
                .build();
        var modernDecoded = ModernScalarMessageSpec.decode(googleMessage.toByteArray());
        assertEquals(modernDecoded, googleMessage);
        var modernEncoded = ModernScalarMessageSpec.encode(modernDecoded);
        var modernDecoded1 = ModernScalarMessageSpec.decode(modernEncoded);
        assertEquals(modernDecoded, modernDecoded1);
    }

    private void assertEquals(ModernScalarMessage modernScalarMessage, ModernScalarMessage modernDecoded) {
        Assertions.assertEquals(modernScalarMessage.fixed32(), modernDecoded.fixed32());
        Assertions.assertEquals(modernScalarMessage.sfixed32(), modernDecoded.sfixed32());
        Assertions.assertEquals(modernScalarMessage.int32(), modernDecoded.int32());
        Assertions.assertEquals(modernScalarMessage.uint32(), modernDecoded.uint32());
        Assertions.assertEquals(modernScalarMessage.fixed64(), modernDecoded.fixed64());
        Assertions.assertEquals(modernScalarMessage.sfixed64(), modernDecoded.sfixed64());
        Assertions.assertEquals(modernScalarMessage.int64(), modernDecoded.int64());
        Assertions.assertEquals(modernScalarMessage.uint64(), modernDecoded.uint64());
        Assertions.assertEquals(modernScalarMessage._float(), modernDecoded._float());
        Assertions.assertEquals(modernScalarMessage._double(), modernDecoded._double());
        Assertions.assertEquals(modernScalarMessage.bool(), modernDecoded.bool());
        Assertions.assertEquals(modernScalarMessage.string(), modernDecoded.string());
        assertEquals(modernScalarMessage.bytes(), modernDecoded.bytes());
    }

    private void assertEquals(ModernScalarMessage modernDecoded, GoogleScalarMessage oldDecoded) {
        Assertions.assertEquals(modernDecoded.fixed32(), oldDecoded.getFixed32());
        Assertions.assertEquals(modernDecoded.sfixed32(), oldDecoded.getSfixed32());
        Assertions.assertEquals(modernDecoded.int32(), oldDecoded.getInt32());
        Assertions.assertEquals(modernDecoded.uint32(), oldDecoded.getUint32());
        Assertions.assertEquals(modernDecoded.fixed64(), oldDecoded.getFixed64());
        Assertions.assertEquals(modernDecoded.sfixed64(), oldDecoded.getSfixed64());
        Assertions.assertEquals(modernDecoded.int64(), oldDecoded.getInt64());
        Assertions.assertEquals(modernDecoded.uint64(), oldDecoded.getUint64());
        Assertions.assertEquals(modernDecoded._float(), oldDecoded.getFloat());
        Assertions.assertEquals(modernDecoded._double(), oldDecoded.getDouble());
        Assertions.assertEquals(modernDecoded.bool(), oldDecoded.getBool());
        Assertions.assertEquals(modernDecoded.string().toString(), oldDecoded.getString());
        assertEquals(modernDecoded.bytes(), oldDecoded.getBytes().toByteArray());
    }

    private void assertEquals(ByteBuffer buffer, byte[] array) {
        assertEquals(buffer, ByteBuffer.wrap(array));
    }

    private void assertEquals(ByteBuffer buffer, ByteBuffer other) {
        Assertions.assertEquals(buffer.remaining(), other.remaining());
        var bufferPosition = buffer.position();
        var otherPosition = other.position();
        for(var i = 0; i < other.remaining(); i++) {
            Assertions.assertEquals(buffer.get(bufferPosition + i), other.get(otherPosition + i));
        }
    }
}
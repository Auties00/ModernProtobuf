package it.auties.proto.compatibility.scalar;

import com.google.protobuf.ByteString;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class GoogleProtocTest {
    @Test
    @SneakyThrows
    public void encodeScalarTypes() {
        var googleMessage = GoogleScalarMessage.newBuilder()
                .setString("Hello, this is an automated test!")
                .setBytes(ByteString.copyFromUtf8("Hello, this is an automated test!"))
                .build();
        var modernDecoded = ModernScalarMessageSpec.decode(googleMessage.toByteArray());
        equals(modernDecoded, googleMessage);
        var modernEncoded = ModernScalarMessageSpec.encode(modernDecoded);
        var modernDecoded1 = ModernScalarMessageSpec.decode(modernEncoded);
        equals(modernDecoded, modernDecoded1);
    }

    private void equals(ModernScalarMessage modernScalarMessage, ModernScalarMessage modernDecoded) {
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
        Assertions.assertArrayEquals(modernScalarMessage.bytes(), modernDecoded.bytes());
    }

    private void equals(ModernScalarMessage modernDecoded, GoogleScalarMessage oldDecoded) {
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
        Assertions.assertEquals(modernDecoded.string(), oldDecoded.getString());
        Assertions.assertArrayEquals(modernDecoded.bytes(), oldDecoded.getBytes().toByteArray());
    }
}
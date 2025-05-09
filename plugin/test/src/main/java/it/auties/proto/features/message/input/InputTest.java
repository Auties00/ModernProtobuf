package it.auties.proto.features.message.input;

import it.auties.protobuf.model.ProtobufString;
import it.auties.protobuf.stream.ProtobufInputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;

public class InputTest {
    private static byte[] source;

    @BeforeAll
    public static void init() {
        var message = new ScalarMessageBuilder()
                .uint32(100)
                .int32(100)
                .string(ProtobufString.wrap("Hello World"))
                .bytes(ByteBuffer.allocate(10))
                .sfixed32(100)
                .sfixed64(100)
                .build();
        source = ScalarMessageSpec.encode(message);
    }

    @Test
    public void testBytes() {
        ScalarMessageSpec.decode(ProtobufInputStream.fromBytes(source));
    }

    @Test
    public void testAvailableInputStream() {
        ScalarMessageSpec.decode(ProtobufInputStream.fromStream(new ByteArrayInputStream(source)));
    }

    @Test
    public void testUnavailableInputStream() {
        final class UnavailableInputStream extends ByteArrayInputStream {
            public UnavailableInputStream(byte[] buf) {
                super(buf);
            }

            @Override
            public int available() {
                return -1;
            }
        }
        ScalarMessageSpec.decode(ProtobufInputStream.fromStream(new UnavailableInputStream(source)));
    }

    @Test
    public void testBuffer() {
        ScalarMessageSpec.decode(ProtobufInputStream.fromBuffer(ByteBuffer.wrap(source)));
    }
}

package it.auties.proto.ci;

import it.auties.proto.ci.message.input.ScalarMessageBuilder;
import it.auties.proto.ci.message.input.ScalarMessageSpec;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufString;
import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.stream.ProtobufInputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;

public class ProtobufInputStreamTest {
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

    @ProtobufMessage
    record ScalarMessage(
            @ProtobufProperty(index = 1, type = ProtobufType.FIXED32)
            int fixed32,
            @ProtobufProperty(index = 2, type = ProtobufType.SFIXED32)
            int sfixed32,
            @ProtobufProperty(index = 3, type = ProtobufType.INT32)
            int int32,
            @ProtobufProperty(index = 4, type = ProtobufType.UINT32)
            int uint32,
            @ProtobufProperty(index = 5, type = ProtobufType.FIXED64)
            long fixed64,
            @ProtobufProperty(index = 6, type = ProtobufType.SFIXED64)
            long sfixed64,
            @ProtobufProperty(index = 7, type = ProtobufType.INT64)
            long int64,
            @ProtobufProperty(index = 8, type = ProtobufType.UINT64)
            long uint64,
            @ProtobufProperty(index = 9, type = ProtobufType.FLOAT)
            float _float,
            @ProtobufProperty(index = 10, type = ProtobufType.DOUBLE)
            double _double,
            @ProtobufProperty(index = 11, type = ProtobufType.BOOL)
            boolean bool,
            @ProtobufProperty(index = 12, type = ProtobufType.STRING)
            ProtobufString string,
            @ProtobufProperty(index = 13, type = ProtobufType.BYTES)
            ByteBuffer bytes
    ) {

    }
}
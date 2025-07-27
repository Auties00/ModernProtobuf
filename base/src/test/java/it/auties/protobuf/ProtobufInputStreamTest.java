package it.auties.protobuf;

import it.auties.protobuf.model.ProtobufString;
import it.auties.protobuf.stream.ProtobufInputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProtobufInputStreamTest {
    @Test
    void testReadInt32() {
        // 300 -> varint encoding: 0xAC, 0x02
        byte[] data = new byte[]{(byte) 0xAC, 0x02};
        var in = ProtobufInputStream.fromBytes(data);
        int value = in.readInt32();
        assertEquals(300, value);
        assertFalse(in.readTag(), "No more tags after reading the varint");
    }

    @Test
    void testReadInt64() {
        // 150 -> varint encoding: 0x96, 0x01
        byte[] data = new byte[]{(byte) 0x96, 0x01};
        var in = ProtobufInputStream.fromBytes(data);
        long value = in.readInt64();
        assertEquals(150L, value);
    }

    @Test
    void testReadBool() {
        var t = ProtobufInputStream.fromBytes(new byte[]{0x01});
        assertTrue(t.readBool());
        var f = ProtobufInputStream.fromBytes(new byte[]{0x00});
        assertFalse(f.readBool());
    }

    @Test
    void testReadFloatAndFixed32() {
        float original = 3.14f;
        int bits = Float.floatToRawIntBits(original);
        ByteBuffer buf = ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(bits);
        buf.flip();
        var in = ProtobufInputStream.fromBuffer(buf);
        float read = in.readFloat();
        assertEquals(original, read, 1e-7f);
        // rewind and test fixed32
        buf.rewind();
        in = ProtobufInputStream.fromBuffer(buf);
        int fixed = in.readFixed32();
        assertEquals(bits, fixed);
    }

    @Test
    void testReadDoubleAndFixed64() {
        double original = 3.141592653589793;
        long bits = Double.doubleToRawLongBits(original);
        ByteBuffer buf = ByteBuffer.allocate(8)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putLong(bits);
        buf.flip();
        var in = ProtobufInputStream.fromBuffer(buf);
        double read = in.readDouble();
        assertEquals(original, read, 1e-15);
        // rewind and test fixed64
        buf.rewind();
        in = ProtobufInputStream.fromBuffer(buf);
        long fixed = in.readFixed64();
        assertEquals(bits, fixed);
    }

    @Test
    void testReadString() {
        String s = "hello";
        byte[] raw = s.getBytes();
        ByteBuffer buf = ByteBuffer.allocate(1 + raw.length)
                .put((byte) raw.length)
                .put(raw);
        buf.flip();
        var in = ProtobufInputStream.fromBuffer(buf);
        ProtobufString.Lazy lazy = in.readString();
        assertEquals(s, lazy.toString());
    }

    @Test
    void testReadBytes() {
        byte[] raw = new byte[]{10, 20, 30};
        ByteBuffer buf = ByteBuffer.allocate(1 + raw.length)
                .put((byte) raw.length)
                .put(raw);
        buf.flip();
        var in = ProtobufInputStream.fromBuffer(buf);
        ByteBuffer out = in.readBytes();
        assertEquals(raw.length, out.remaining());
        byte[] got = new byte[out.remaining()];
        out.get(got);
        assertArrayEquals(raw, got);
    }

    @Test
    void testReadTag() {
        // field=5, wireType=5 (fixed32) -> tag = (5<<3)|5 = 0x2D
        var in = ProtobufInputStream.fromBytes(new byte[]{0x2D});
        assertTrue(in.readTag());
        assertFalse(in.readTag());
    }

    @Test
    void testReadFromStream() {
        byte[] data = new byte[]{0x08, (byte) 0x96, 0x01}; // tag=1,varint 150
        var stream = new ByteArrayInputStream(data);
        var in = ProtobufInputStream.fromStream(stream);
        assertTrue(in.readTag());
        int v = in.readInt32();
        assertEquals(150, v);
        assertFalse(in.readTag());
    }

    @Test
    void testReadUnknown() {
        // field=1, wireType=2 -> tag 0x0A, length=3, payload "abc"
        byte[] payload = new byte[]{0x0A, 0x03, 'a', 'b', 'c'};
        var in1 = ProtobufInputStream.fromBytes(payload);
        assertTrue(in1.readTag());
        Object o1 = in1.readUnknown();
        assertTrue(o1 instanceof ByteBuffer);
        ByteBuffer buf1 = (ByteBuffer) o1;
        byte[] got1 = new byte[buf1.remaining()];
        buf1.get(got1);
        assertArrayEquals(new byte[]{'a','b','c'}, got1);

        var in2 = ProtobufInputStream.fromBytes(payload);
        assertTrue(in2.readTag());
        Object o2 = in2.readUnknown();
        assertTrue(o2 instanceof ByteBuffer);
        ByteBuffer buf2 = (ByteBuffer) o2;
        byte[] got2 = new byte[buf2.remaining()];
        buf2.get(got2);
        assertArrayEquals(new byte[]{'a','b','c'}, got2);
    }

    @Test
    void testReadPackedInt32() {
        // field=1, wireType=2 -> tag=0x0A; two ints 150,151 => each varint 2 bytes => length=4
        byte[] data = new byte[]{
                0x0A, 0x04,
                (byte) 0x96, 0x01,
                (byte) 0x97, 0x01
        };
        var in = ProtobufInputStream.fromBytes(data);
        assertTrue(in.readTag());
        List<Integer> list = in.readInt32Packed();
        assertEquals(2, list.size());
        assertEquals(150, list.get(0));
        assertEquals(151, list.get(1));
        assertFalse(in.readTag());
    }
}
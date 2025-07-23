package it.auties.protobuf;

import it.auties.protobuf.model.ProtobufWireType;
import it.auties.protobuf.stream.ProtobufOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import it.auties.protobuf.model.ProtobufString;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class ProtobufOutputStreamTest {
    @Test
    void testGetVarIntSize() {
        assertEquals(1, ProtobufOutputStream.getVarIntSize(0));
        assertEquals(1, ProtobufOutputStream.getVarIntSize(127));
        assertEquals(2, ProtobufOutputStream.getVarIntSize(128));
        assertEquals(2, ProtobufOutputStream.getVarIntSize(16383));
        assertEquals(3, ProtobufOutputStream.getVarIntSize(16384));
        assertEquals(10, ProtobufOutputStream.getVarIntSize(-1L));
    }

    @Test
    void testGetFieldSize() {
        // fieldNumber=1, wireType=0 => tag=8, varint size=1
        assertEquals(1, ProtobufOutputStream.getFieldSize(1, ProtobufWireType.WIRE_TYPE_VAR_INT));
        // fieldNumber=2, wireType=5 => tag=(2<<3)|5=21, size=1
        assertEquals(1, ProtobufOutputStream.getFieldSize(2, ProtobufWireType.WIRE_TYPE_FIXED32));
    }

    @Test
    void testGetStringSize() {
        ProtobufString small = ProtobufString.wrap("A");
        // encodedLength=1, varintSize(1)=1 => total=2
        assertEquals(2, ProtobufOutputStream.getStringSize(small));
    }

    @Test
    void testGetBytesSize() {
        var buffer = ByteBuffer.wrap(new byte[]{1, 2, 3, 4});
        // remaining=4, varintSize(4)=1 => total=5
        assertEquals(5, ProtobufOutputStream.getBytesSize(buffer));
        assertEquals(0, ProtobufOutputStream.getBytesSize(null));
    }

    @Test
    void testGetPackedSizes() {
        assertEquals(5, ProtobufOutputStream.getVarIntPackedSize(1, Arrays.asList(1, 2, 3)));
        // valuesSize = 3*4 = 12, tag varint=1, length varint=1 => total=14
        assertEquals(14, ProtobufOutputStream.getFixed32PackedSize(1, Arrays.asList(1, 2, 3)));
        // valuesSize = 2*8 = 16, tag=1, length=1 => 18
        assertEquals(18, ProtobufOutputStream.getFixed64PackedSize(1, Arrays.asList(10L, 20L)));
        // valuesSize = 5, tag=1, length=1 => 7
        assertEquals(7, ProtobufOutputStream.getBoolPackedSize(1, Collections.nCopies(5, 1L)));
    }

    @Test
    void testWriteInt32AndUInt32ToStream() {
        var baos = new ByteArrayOutputStream();
        var out = ProtobufOutputStream.toStream(baos);
        out.writeInt32(1, 150);
        out.writeUInt32(2, 300);
        var actual = baos.toByteArray();
        // writeInt32: tag=0x08, varint(150)=[0x96 0x01]
        // writeUInt32: tag=(2<<3)|0=0x10, varint(300)=[0xAC 0x02]
        var expected = new byte[]{0x08, (byte) 0x96, 0x01, 0x10, (byte) 0xAC, 0x02};
        assertArrayEquals(expected, actual);
    }

    @Test
    void testWriteInt32PackedAndUInt32Packed() {
        var baos = new ByteArrayOutputStream();
        var out = ProtobufOutputStream.toStream(baos);
        out.writeInt32Packed(1, Arrays.asList(1, 2, 3));
        out.writeUInt32Packed(2, Arrays.asList(4, 5));
        var actual = baos.toByteArray();
        // int32Packed: tag=0x0A, length=3, bytes=1,2,3
        // uint32Packed: tag=0x12, length=2, bytes=4,5
        var expected = new byte[]{0x0A, 0x03, 0x01, 0x02, 0x03, 0x12, 0x02, 0x04, 0x05};
        assertArrayEquals(expected, actual);
    }

    @Test
    void testWriteFloatAndFloatPacked() {
        var a = 3.14f;
        var b = 1.0f;
        var c = 2.0f;
        var aBits = Float.floatToRawIntBits(a);
        var bBits = Float.floatToRawIntBits(b);
        var cBits = Float.floatToRawIntBits(c);
        var baos = new ByteArrayOutputStream();
        var out = ProtobufOutputStream.toStream(baos);
        out.writeFloat(1, a);
        out.writeFloatPacked(2, Arrays.asList(b, c));
        var actual = baos.toByteArray();
        // writeFloat: tag=(1<<3)|5=13, then 4 bytes LE of aBits
        // writeFloatPacked: tag=0x12, length=8, then two LE floats
        var buf = ByteBuffer.allocate(1 + 4 + 1 + 1 + 8);
        buf.put((byte) 0x0D);
        buf.putInt(Integer.reverseBytes(aBits));
        buf.put((byte) 0x12);
        buf.put((byte) 8);
        buf.putInt(Integer.reverseBytes(bBits));
        buf.putInt(Integer.reverseBytes(cBits));
        var expected = buf.array();
        assertArrayEquals(expected, actual);
    }

    @Test
    void testWriteFixed32AndFixed32Packed() {
        var v1 = 1;
        var v2 = 2;
        var baos = new ByteArrayOutputStream();
        var out = ProtobufOutputStream.toStream(baos);
        out.writeFixed32(1, v1);
        out.writeFixed32Packed(2, Arrays.asList(v1, v2));
        var actual = baos.toByteArray();
        // writeFixed32: tag=13, then 01 00 00 00
        // packed: tag=0x12, length=8, then 01 00 00 00,02 00 00 00
        var expected = new byte[]{0x0D, 0x01, 0x00, 0x00, 0x00, 0x12, 0x08, 0x01, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00};
        assertArrayEquals(expected, actual);
    }

    @Test
    void testWriteDoubleAndDoublePacked() {
        var x = 3.14159;
        var y = 1.0;
        var z = 2.0;
        var xBits = Double.doubleToRawLongBits(x);
        var yBits = Double.doubleToRawLongBits(y);
        var zBits = Double.doubleToRawLongBits(z);
        var baos = new ByteArrayOutputStream();
        var out = ProtobufOutputStream.toStream(baos);
        out.writeDouble(1, x);
        out.writeDoublePacked(2, Arrays.asList(y, z));
        var actual = baos.toByteArray();
        var buf = ByteBuffer.allocate(1 + 8 + 1 + 1 + 16);
        buf.put((byte) ((1 << 3) | ProtobufWireType.WIRE_TYPE_FIXED64));
        buf.putLong(Long.reverseBytes(xBits));
        buf.put((byte) ((2 << 3) | ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED));
        buf.put((byte) 16);
        buf.putLong(Long.reverseBytes(yBits));
        buf.putLong(Long.reverseBytes(zBits));
        assertArrayEquals(buf.array(), actual);
    }

    @Test
    void testWriteGroupTags() {
        var baos = new ByteArrayOutputStream();
        var out = ProtobufOutputStream.toStream(baos);
        out.writeGroupStart(3);
        out.writeGroupEnd(3);
        var actual = baos.toByteArray();
        // start tag = (3<<3)|3 = 27, end tag = (3<<3)|4 = 28
        assertArrayEquals(new byte[]{27, 28}, actual);
    }

    @Test
    void testWriteTagAlone() {
        var baos = new ByteArrayOutputStream();
        var out = ProtobufOutputStream.toStream(baos);
        out.writeTag(2, ProtobufWireType.WIRE_TYPE_FIXED64);
        assertArrayEquals(new byte[]{(byte) ((2 << 3) | ProtobufWireType.WIRE_TYPE_FIXED64)}, baos.toByteArray());
    }
}
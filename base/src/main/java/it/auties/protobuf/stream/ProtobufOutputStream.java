package it.auties.protobuf.stream;

import it.auties.protobuf.exception.ProtobufSerializationException;
import it.auties.protobuf.model.ProtobufString;
import it.auties.protobuf.model.ProtobufWireType;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

/**
 * An abstract output stream for writing Protocol Buffer encoded data.
 * <p>
 * This class provides a comprehensive API for serializing Protocol Buffer messages to various
 * output destinations, including byte arrays, ByteBuffers, and OutputStreams.
 * </p>
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Writing to a byte array
 * ProtobufOutputStream<byte[]> output = ProtobufOutputStream.toBytes(1024);
 * output.writeString(1, ProtobufString.wrap("Hello"));
 * output.writeInt32(2, 42);
 * output.writeBool(3, true);
 * byte[] result = output.toOutput();
 * }</pre>
 *
 * <h2>Size Calculation:</h2>
 * <p>
 * The class provides static utility methods for calculating the serialized size of various
 * field types before writing, which is crucial for performance.
 * </p>
 *
 * @param <OUTPUT> the type of output this stream produces (byte[], ByteBuffer, OutputStream, ...)
 *
 * @see ProtobufInputStream
 */
@SuppressWarnings("unused")
public abstract class ProtobufOutputStream<OUTPUT> {
    public static int getFieldSize(long fieldIndex, int wireType) {
        return getVarIntSize(ProtobufWireType.makeTag(fieldIndex, wireType));
    }

    // Long values go from [-2^63, 2^63)
    // A negative var-int always take up 10 bits
    // A positive var int takes up log_2(value) / 7 + 1
    // Constants were folded here to save time
    public static int getVarIntSize(long value) {
        if(value < 0) {
            return 10;
        }else if (value < 128) {
            return 1;
        } else if (value < 16384) {
            return 2;
        } else if (value < 2097152) {
            return 3;
        } else if (value < 268435456) {
            return 4;
        } else if(value < 34359738368L) {
            return 5;
        }else if(value < 4398046511104L) {
            return 6;
        }else if(value < 562949953421312L) {
            return 7;
        }else if(value < 72057594037927936L) {
            return 8;
        }else {
            return 9;
        }
    }

    public static int getStringSize(ProtobufString value) {
        var count = value.encodedLength();
        return getVarIntSize(count) + count;
    }

    public static int getBytesSize(ByteBuffer value) {
        if(value == null) {
            return 0;
        }

        var length = value.remaining();
        return getVarIntSize(length) + length;
    }

    public static int getBytesSize(byte[] value) {
        if(value == null) {
            return 0;
        }

        return getVarIntSize(value.length) + value.length;
    }

    public static int getVarIntPackedSize(long fieldIndex, Collection<? extends Number> values) {
        if(values == null){
            return 0;
        }

        var size = getFieldSize(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        var valueSize = 0;
        for (var value : values) {
            if(value != null) {
                valueSize += getVarIntSize(value.longValue());
            }
        }
        return getFieldSize(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED)
                + getVarIntSize(valueSize)
                + valueSize;
    }

    public static int getFixed32PackedSize(long fieldIndex, Collection<? extends Number> values) {
        if(values == null){
            return 0;
        }

        var valuesSize = values.size() * Integer.BYTES;
        return getFieldSize(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED)
                + getVarIntSize(valuesSize)
                + valuesSize;
    }

    public static int getFixed64PackedSize(long fieldIndex, Collection<? extends Number> values) {
        if(values == null){
            return 0;
        }

        var valuesSize = values.size() * Long.BYTES;
        return getFieldSize(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED)
                + getVarIntSize(valuesSize)
                + valuesSize;
    }

    public static int getBoolPackedSize(long fieldIndex, Collection<Long> values) {
        if(values == null){
            return 0;
        }

        var valuesSize = values.size();
        return getFieldSize(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED)
                + getVarIntSize(valuesSize)
                + valuesSize;
    }

    public static ProtobufOutputStream<byte[]> toBytes(int length) {
        return new ProtobufOutputStream.Bytes(new byte[length], 0);
    }

    public static ProtobufOutputStream<byte[]> toBytes(byte[] bytes, int offset) {
        return new ProtobufOutputStream.Bytes(bytes, offset);
    }

    public static ProtobufOutputStream<ByteBuffer> toBuffer(ByteBuffer buffer) {
        return new ProtobufOutputStream.Buffer(buffer);
    }

    public static ProtobufOutputStream<OutputStream> toStream(OutputStream buffer) {
        return new ProtobufOutputStream.Stream(buffer);
    }

    protected ProtobufOutputStream() {

    }
    
    public void writeGroupStart(long fieldIndex) {
        writeField(fieldIndex, ProtobufWireType.WIRE_TYPE_START_OBJECT);
    }

    public void writeGroupEnd(long fieldIndex) {
        writeField(fieldIndex, ProtobufWireType.WIRE_TYPE_END_OBJECT);
    }

    public void writeInt32Packed(long fieldIndex, Collection<Integer> values) {
        if(values == null){
            return;
        }

        var size = 0;
        for (var value : values) {
            if(value != null) {
                size += getVarIntSize(value);
            }
        }
        writeField(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeFieldValue(size);
        for (var value : values) {
            if(value != null) {
                writeFieldValue(value);
            }
        }
    }

    public void writeInt32(long fieldIndex, Integer value) {
        if(value == null){
            return;
        }

        writeField(fieldIndex, ProtobufWireType.WIRE_TYPE_VAR_INT);
        writeFieldValue(value);
    }

    public void writeUInt32Packed(long fieldIndex, Collection<Integer> values) {
        if(values == null){
            return;
        }

        var size = 0;
        for (var value : values) {
            if(value != null) {
                size += getVarIntSize(value);
            }
        }
        writeField(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeFieldValue(size);
        for (var value : values) {
            if(value != null) {
                writeFieldValue(value);
            }
        }
    }

    public void writeUInt32(long fieldIndex, Integer value) {
        if(value == null){
            return;
        }

        writeField(fieldIndex, ProtobufWireType.WIRE_TYPE_VAR_INT);
        writeFieldValue(value);
    }

    public void writeFloatPacked(long fieldIndex, Collection<Float> values) {
        if(values == null){
            return;
        }

        var size = 0;
        for (var value : values) {
            if(value != null) {
               size += Float.BYTES;
            }
        }
        writeField(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeFieldValue(size);
        for (var value : values) {
            if(value != null) {
                writeFixed32NoTag(Float.floatToRawIntBits(value));
            }
        }
    }

    public void writeFloat(long fieldIndex, Float value) {
        if(value == null){
            return;
        }

        writeFixed32(fieldIndex, Float.floatToRawIntBits(value));
    }

    public void writeFixed32Packed(long fieldIndex, Collection<Integer> values) {
        if(values == null){
            return;
        }

        var size = 0;
        for (var value : values) {
            if(value != null) {
                size += Integer.BYTES;
            }
        }
        writeField(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeFieldValue(size);
        for (var value : values) {
            if(value != null) {
                writeFixed32NoTag(value);
            }
        }
    }

    public void writeFixed32(long fieldIndex, Integer value) {
        if(value == null){
            return;
        }

        writeField(fieldIndex, ProtobufWireType.WIRE_TYPE_FIXED32);
        writeFixed32NoTag(value);
    }

    private void writeFixed32NoTag(Integer value) {
        writeFieldValue((byte) (value & 0xFF));
        writeFieldValue((byte) ((value >> 8) & 0xFF));
        writeFieldValue((byte) ((value >> 16) & 0xFF));
        writeFieldValue((byte) ((value >> 24) & 0xFF));
    }

    public void writeInt64Packed(long fieldIndex, Collection<Long> values) {
        if(values == null){
            return;
        }

        var size = 0;
        for (var value : values) {
            if(value != null) {
                size += Long.BYTES;
            }
        }
        writeField(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeFieldValue(size);
        for (var value : values) {
            if(value != null) {
                writeFixed64NoTag(value);
            }
        }
    }

    public void writeInt64(long fieldIndex, Long value) {
        if(value == null){
            return;
        }

        writeUInt64(fieldIndex, value);
    }

    public void writeUInt64Packed(long fieldIndex, Collection<Long> values) {
        if(values == null){
            return;
        }

        var size = 0;
        for (var value : values) {
            size += getVarIntSize(value);
        }
        writeField(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeFieldValue(size);
        for (var value : values) {
            if(value != null) {
                writeFieldValue(value);
            }
        }
    }

    public void writeUInt64(long fieldIndex, Long value) {
        if(value == null){
            return;
        }

        writeField(fieldIndex, ProtobufWireType.WIRE_TYPE_VAR_INT);
        writeFieldValue(value);
    }

    public void writeDoublePacked(long fieldIndex, Collection<Double> values) {
        if(values == null){
            return;
        }

        var size = 0;
        for (var value : values) {
            if(value != null) {
                size += Double.BYTES;
            }
        }
        writeField(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeFieldValue(size);
        for (var value : values) {
            if(value != null) {
                writeFixed64NoTag(Double.doubleToRawLongBits(value));
            }
        }
    }

    public void writeDouble(long fieldIndex, Double value) {
        if(value == null){
            return;
        }

        writeFixed64(fieldIndex, Double.doubleToRawLongBits(value));
    }

    public void writeFixed64Packed(long fieldIndex, Collection<Long> values) {
        if(values == null){
            return;
        }

        var size = 0;
        for (var value : values) {
            if(value != null) {
                size += Long.BYTES;
            }
        }
        writeField(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeFieldValue(size);
        for (var value : values) {
            if(value != null) {
                writeFixed64NoTag(value);
            }
        }
    }

    public void writeFixed64(long fieldIndex, Long value) {
        if(value == null){
            return;
        }

        writeField(fieldIndex, ProtobufWireType.WIRE_TYPE_FIXED64);
        writeFixed64NoTag(value);
    }

    private void writeFixed64NoTag(Long value) {
        writeFieldValue((byte) ((int) ((long) value) & 0xFF));
        writeFieldValue((byte) ((int) (value >> 8) & 0xFF));
        writeFieldValue((byte) ((int) (value >> 16) & 0xFF));
        writeFieldValue((byte) ((int) (value >> 24) & 0xFF));
        writeFieldValue((byte) ((int) (value >> 32) & 0xFF));
        writeFieldValue((byte) ((int) (value >> 40) & 0xFF));
        writeFieldValue((byte) ((int) (value >> 48) & 0xFF));
        writeFieldValue((byte) ((int) (value >> 56) & 0xFF));
    }

    public void writeBoolPacked(long fieldIndex, Collection<Boolean> values) {
        if(values == null){
            return;
        }

        var size = 0;
        for (var value : values) {
            if(value != null) {
                size++;
            }
        }
        writeField(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeFieldValue(size);
        for (var value : values) {
            if(value != null) {
                writeFieldValue((byte) (value ? 1 : 0));
            }
        }
    }

    public void writeBool(long fieldIndex, Boolean value) {
        if(value == null){
            return;
        }

        writeField(fieldIndex, ProtobufWireType.WIRE_TYPE_VAR_INT);
        writeFieldValue((byte) (value ? 1 : 0));
    }

    public void writeString(long fieldIndex, ProtobufString value) {
        if(value == null){
            return;
        }

        value.write(fieldIndex, this);
    }

    public void writeBytes(long fieldIndex, ByteBuffer value) {
        if(value == null){
            return;
        }

        writeField(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        var size = value.remaining();
        writeFieldValue(size);
        writeFieldValue(value);
    }

    public void writeBytes(long fieldIndex, byte[] value) {
        if(value == null){
            return;
        }

        writeField(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeFieldValue(value.length);
        writeFieldValue(value);
    }

    public void writeBytes(long fieldIndex, byte[] value, int offset, int size) {
        if(value == null){
            return;
        }

        writeField(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeFieldValue(size);
        writeFieldValue(value, offset, size);
    }

    public void writeMessage(long fieldIndex, int size) {
        writeField(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeFieldValue(size);
    }

    public void writeField(long fieldIndex, int wireType) {
        writeFieldValue(ProtobufWireType.makeTag(fieldIndex, wireType));
    }

    public void writeFieldValue(long value) {
        while (true) {
            if ((value & ~0x7FL) == 0) {
                writeFieldValue((byte) value);
                return;
            } else {
                writeFieldValue((byte) (((int) value & 0x7F) | 0x80));
                value >>>= 7;
            }
        }
    }

    public abstract void writeFieldValue(byte entry);
    public abstract void writeFieldValue(byte[] entry);
    public abstract void writeFieldValue(byte[] entry, int offset, int length);
    public abstract void writeFieldValue(ByteBuffer entry);
    public abstract void writeFieldValue(String value);
    public abstract OUTPUT toOutput();

    private static final class Stream extends ProtobufOutputStream<OutputStream> {
        private final OutputStream outputStream;
        private Stream(OutputStream outputStream) {
            this.outputStream = outputStream;
        }

        @Override
        public void writeFieldValue(byte entry) {
            try {
                outputStream.write(entry);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void writeFieldValue(byte[] entry) {
            try {
                outputStream.write(entry);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void writeFieldValue(byte[] entry, int offset, int length) {
            try {
                outputStream.write(entry, offset, length);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void writeFieldValue(ByteBuffer entry) {
            try {
                if(entry.hasArray()) {
                    outputStream.write(entry.array(), entry.arrayOffset() + entry.position(), entry.remaining());
                }else {
                    var size = entry.remaining();
                    var bufferPosition = entry.position();
                    for(var i = 0; i < size; i++) {
                        outputStream.write(entry.get(bufferPosition + i));
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void writeFieldValue(String str) {
            try {
                var buffer = new byte[Integer.BYTES];
                var len = str.length();
                for (var i = 0; i < len; ) {
                    final int codepoint = str.codePointAt(i);
                    int byteCount;
                    if (codepoint < 0x80) {
                        buffer[0] = (byte) codepoint;
                        byteCount = 1;
                    } else if (codepoint < 0x800) {
                        buffer[0] = (byte) (0xC0 | (codepoint >> 6));
                        buffer[1] = (byte) (0x80 | (codepoint & 0x3F));
                        byteCount = 2;
                    } else if (codepoint < 0x10000) {
                        buffer[0] = (byte) (0xE0 | (codepoint >> 12));
                        buffer[1] = (byte) (0x80 | ((codepoint >> 6) & 0x3F));
                        buffer[2] = (byte) (0x80 | (codepoint & 0x3F));
                        byteCount = 3;
                    } else if (codepoint < 0x110000) {
                        buffer[0] = (byte) (0xF0 | (codepoint >> 18));
                        buffer[1] = (byte) (0x80 | ((codepoint >> 12) & 0x3F));
                        buffer[2] = (byte) (0x80 | ((codepoint >> 6) & 0x3F));
                        buffer[3] = (byte) (0x80 | (codepoint & 0x3F));
                        byteCount = 4;
                    } else {
                        throw new AssertionError("Unexpected Unicode codepoint: " + codepoint);
                    }

                    outputStream.write(buffer, 0, byteCount);
                    i += Character.charCount(codepoint);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public OutputStream toOutput() {
            return outputStream;
        }
    }

    private static final class Bytes extends ProtobufOutputStream<byte[]> {
        private final byte[] buffer;
        private int position;
        private Bytes(byte[] buffer, int offset) {
            this.buffer = buffer;
            this.position = offset;
        }

        @Override
        public void writeFieldValue(byte entry) {
            buffer[position++] = entry;
        }

        @Override
        public void writeFieldValue(byte[] entry) {
            var length = entry.length;
            System.arraycopy(entry, 0, buffer, position, length);
            position += length;
        }

        @Override
        public void writeFieldValue(byte[] entry, int offset, int length) {
            System.arraycopy(entry, offset, buffer, position, length);
            position += length;
        }

        @Override
        public void writeFieldValue(ByteBuffer entry) {
            var length = entry.remaining();
            entry.get(entry.position(), buffer, position, length);
            position += length;
        }

        @Override
        public void writeFieldValue(String value) {
            var result = StandardCharsets.UTF_8
                    .newEncoder()
                    .encode(CharBuffer.wrap(value), ByteBuffer.wrap(buffer), true);
            if (!result.isError()) {
                return;
            }

            if(result.isUnderflow()) {
                throw new ProtobufSerializationException("Buffer underflow");
            }else if(result.isOverflow()) {
                throw new ProtobufSerializationException("Buffer overflow");
            }else if(result.isMalformed()) {
                throw new ProtobufSerializationException("Buffer malformed");
            } else if(result.isUnmappable()) {
                throw new ProtobufSerializationException("Buffer unmappable");
            }else {
                throw new IllegalStateException("Unknown error");
            }
        }

        @Override
        public byte[] toOutput() {
            var delta = buffer.length - position;
            if(delta != 0) {
                throw ProtobufSerializationException.sizeMismatch(delta);
            }

            return buffer;
        }
    }

    private static final class Buffer extends ProtobufOutputStream<ByteBuffer> {
        private final ByteBuffer buffer;
        private Buffer(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        @Override
        public void writeFieldValue(byte entry) {
            buffer.put(entry);
        }

        @Override
        public void writeFieldValue(byte[] entry) {
            buffer.put(entry);
        }

        @Override
        public void writeFieldValue(byte[] entry, int offset, int length) {
            buffer.put(entry, offset, length);
        }

        @Override
        public void writeFieldValue(ByteBuffer entry) {
            buffer.put(entry);
        }

        @Override
        public void writeFieldValue(String value) {
            var result = StandardCharsets.UTF_8
                    .newEncoder()
                    .encode(CharBuffer.wrap(value), buffer, true);
            if (!result.isError()) {
                return;
            }

            if(result.isUnderflow()) {
                throw new ProtobufSerializationException("Buffer underflow");
            }else if(result.isOverflow()) {
                throw new ProtobufSerializationException("Buffer overflow");
            }else if(result.isMalformed()) {
                throw new ProtobufSerializationException("Buffer malformed");
            } else if(result.isUnmappable()) {
                throw new ProtobufSerializationException("Buffer unmappable");
            }else {
                throw new IllegalStateException("Unknown error");
            }
        }

        @Override
        public ByteBuffer toOutput() {
            var remaining = buffer.limit() - buffer.position();
            if(remaining != 0) {
                throw ProtobufSerializationException.sizeMismatch(remaining);
            }

            buffer.flip();
            return buffer;
        }
    }
}

package it.auties.protobuf.stream;

import it.auties.protobuf.exception.ProtobufSerializationException;
import it.auties.protobuf.model.ProtobufString;
import it.auties.protobuf.model.ProtobufWireType;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Collection;

@SuppressWarnings("unused")
public abstract class ProtobufOutputStream<OUTPUT> {
    public static int getFieldSize(int fieldNumber, int wireType) {
        return getVarIntSize(ProtobufWireType.makeTag(fieldNumber, wireType));
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

    public static int getStringSize(ProtobufString value) {
        var count = value.encodedLength();
        return getVarIntSize(count) + count;
    }

    public static int getBytesSize(ByteBuffer value) {
        if(value == null) {
            return 0;
        }

        return getVarIntSize(value.remaining()) + value.remaining();
    }

    public static int getVarIntPackedSize(int fieldNumber, Collection<? extends Number> values) {
        if(values == null){
            return 0;
        }

        var size = getFieldSize(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        var valueSize = 0;
        for (var value : values) {
            if(value != null) {
                valueSize += getVarIntSize(value.longValue());
            }
        }
        return getFieldSize(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED)
                + getVarIntSize(valueSize)
                + valueSize;
    }

    public int getFixed32PackedSize(int fieldNumber, Collection<? extends Number> values) {
        if(values == null){
            return 0;
        }

        var valuesSize = values.size() * 4;
        return getFieldSize(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED)
                + getVarIntSize(valuesSize)
                + valuesSize;
    }

    public static int getFixed64PackedSize(int fieldNumber, Collection<? extends Number> values) {
        if(values == null){
            return 0;
        }

        var valuesSize = values.size() * 8;
        return getFieldSize(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED)
                + getVarIntSize(valuesSize)
                + valuesSize;
    }

    public static int getBoolPackedSize(int fieldNumber, Collection<Long> values) {
        if(values == null){
            return 0;
        }

        var valuesSize = values.size();
        return getFieldSize(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED)
                + getVarIntSize(valuesSize)
                + valuesSize;
    }

    protected ProtobufOutputStream() {

    }

    public void writeTag(int fieldNumber, int wireType) {
        writeRawVarInt(ProtobufWireType.makeTag(fieldNumber, wireType));
    }

    public void writeGroupStart(int fieldNumber) {
        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_START_OBJECT);
    }

    public void writeGroupEnd(int fieldNumber) {
        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_END_OBJECT);
    }

    public void writeInt32Packed(int fieldNumber, Collection<Integer> values) {
        if(values == null){
            return;
        }

        var size = 0;
        for (var value : values) {
            if(value != null) {
                size += getVarIntSize(value);
            }
        }
        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeRawVarInt(size);
        for (var value : values) {
            if(value != null) {
                writeRawVarInt(value);
            }
        }
    }

    public void writeInt32(int fieldNumber, Integer value) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_VAR_INT);
        writeRawVarInt(value);
    }

    public void writeUInt32Packed(int fieldNumber, Collection<Integer> values) {
        if(values == null){
            return;
        }

        var size = 0;
        for (var value : values) {
            if(value != null) {
                size += getVarIntSize(value);
            }
        }
        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeRawVarInt(size);
        for (var value : values) {
            if(value != null) {
                writeRawVarInt(value);
            }
        }
    }

    public void writeUInt32(int fieldNumber, Integer value) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_VAR_INT);
        writeRawVarInt(value);
    }

    public void writeFloatPacked(int fieldNumber, Collection<Float> values) {
        if(values == null){
            return;
        }

        var size = 0;
        for (var value : values) {
            if(value != null) {
               size += Float.BYTES;
            }
        }
        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeRawVarInt(size);
        for (var value : values) {
            if(value != null) {
                writeFixed32NoTag(Float.floatToRawIntBits(value));
            }
        }
    }

    public void writeFloat(int fieldNumber, Float value) {
        if(value == null){
            return;
        }

        writeFixed32(fieldNumber, Float.floatToRawIntBits(value));
    }

    public void writeFixed32Packed(int fieldNumber, Collection<Integer> values) {
        if(values == null){
            return;
        }

        var size = 0;
        for (var value : values) {
            if(value != null) {
                size += Integer.BYTES;
            }
        }
        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeRawVarInt(size);
        for (var value : values) {
            if(value != null) {
                writeFixed32NoTag(value);
            }
        }
    }

    public void writeFixed32(int fieldNumber, Integer value) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_FIXED32);
        writeFixed32NoTag(value);
    }

    private void writeFixed32NoTag(Integer value) {
        writeRaw((byte) (value & 0xFF));
        writeRaw((byte) ((value >> 8) & 0xFF));
        writeRaw((byte) ((value >> 16) & 0xFF));
        writeRaw((byte) ((value >> 24) & 0xFF));
    }

    public void writeInt64Packed(int fieldNumber, Collection<Long> values) {
        if(values == null){
            return;
        }

        var size = 0;
        for (var value : values) {
            if(value != null) {
                size += Long.BYTES;
            }
        }
        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeRawVarInt(size);
        for (var value : values) {
            if(value != null) {
                writeFixed64NoTag(value);
            }
        }
    }

    public void writeInt64(int fieldNumber, Long value) {
        if(value == null){
            return;
        }

        writeUInt64(fieldNumber, value);
    }

    public void writeUInt64Packed(int fieldNumber, Collection<Long> values) {
        if(values == null){
            return;
        }

        var size = 0;
        for (var value : values) {
            size += getVarIntSize(value);
        }
        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeRawVarInt(size);
        for (var value : values) {
            if(value != null) {
                writeRawVarInt(value);
            }
        }
    }

    public void writeUInt64(int fieldNumber, Long value) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_VAR_INT);
        writeRawVarInt(value);
    }

    public void writeDoublePacked(int fieldNumber, Collection<Double> values) {
        if(values == null){
            return;
        }

        var size = 0;
        for (var value : values) {
            if(value != null) {
                size += Double.BYTES;
            }
        }
        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeRawVarInt(size);
        for (var value : values) {
            if(value != null) {
                writeFixed64NoTag(Double.doubleToRawLongBits(value));
            }
        }
    }

    public void writeDouble(int fieldNumber, Double value) {
        if(value == null){
            return;
        }

        writeFixed64(fieldNumber, Double.doubleToRawLongBits(value));
    }

    public void writeFixed64Packed(int fieldNumber, Collection<Long> values) {
        if(values == null){
            return;
        }

        var size = 0;
        for (var value : values) {
            if(value != null) {
                size += Long.BYTES;
            }
        }
        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeRawVarInt(size);
        for (var value : values) {
            if(value != null) {
                writeFixed64NoTag(value);
            }
        }
    }

    public void writeFixed64(int fieldNumber, Long value) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_FIXED64);
        writeFixed64NoTag(value);
    }

    private void writeFixed64NoTag(Long value) {
        writeRaw((byte) ((int) ((long) value) & 0xFF));
        writeRaw((byte) ((int) (value >> 8) & 0xFF));
        writeRaw((byte) ((int) (value >> 16) & 0xFF));
        writeRaw((byte) ((int) (value >> 24) & 0xFF));
        writeRaw((byte) ((int) (value >> 32) & 0xFF));
        writeRaw((byte) ((int) (value >> 40) & 0xFF));
        writeRaw((byte) ((int) (value >> 48) & 0xFF));
        writeRaw((byte) ((int) (value >> 56) & 0xFF));
    }

    public void writeBoolPacked(int fieldNumber, Collection<Boolean> values) {
        if(values == null){
            return;
        }

        var size = 0;
        for (var value : values) {
            if(value != null) {
                size++;
            }
        }
        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeRawVarInt(size);
        for (var value : values) {
            if(value != null) {
                writeRaw((byte) (value ? 1 : 0));
            }
        }
    }

    public void writeBool(int fieldNumber, Boolean value) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_VAR_INT);
        writeRaw((byte) (value ? 1 : 0));
    }

    public void writeString(int fieldNumber, ProtobufString value) {
        if(value == null){
            return;
        }

        value.write(fieldNumber, this);
    }

    public void writeBytes(int fieldNumber, ByteBuffer value) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        var size = value.remaining();
        writeRawVarInt(size);
        writeRaw(value);
    }

    public void writeBytes(int fieldNumber, byte[] value) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeRawVarInt(value.length);
        writeRaw(value);
    }

    public void writeBytes(int fieldNumber, byte[] value, int offset, int size) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeRawVarInt(size);
        writeRaw(value, offset, size);
    }

    public void writeMessage(int fieldNumber, int size) {
        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeRawVarInt(size);
    }

    public void writeRawVarInt(long value) {
        while (true) {
            if ((value & ~0x7FL) == 0) {
                writeRaw((byte) value);
                return;
            } else {
                writeRaw((byte) (((int) value & 0x7F) | 0x80));
                value >>>= 7;
            }
        }
    }

    public abstract void writeRaw(byte entry);
    public abstract void writeRaw(byte[] entry);
    public abstract void writeRaw(byte[] entry, int offset, int length);
    public abstract void writeRaw(ByteBuffer entry);
    public abstract OUTPUT toOutput();

    private static final class Stream extends ProtobufOutputStream<OutputStream> {
        private final OutputStream outputStream;
        private Stream(OutputStream outputStream) {
            this.outputStream = outputStream;
        }

        @Override
        public void writeRaw(byte entry) {
            try {
                outputStream.write(entry);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void writeRaw(byte[] entry) {
            try {
                outputStream.write(entry);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void writeRaw(byte[] entry, int offset, int length) {
            try {
                outputStream.write(entry, offset, length);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void writeRaw(ByteBuffer entry) {
            try {
                var size = entry.remaining();
                var bufferPosition = entry.position();
                for(var i = 0; i < size; i++) {
                    outputStream.write(entry.get(bufferPosition + i));
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
        public void writeRaw(byte entry) {
            buffer[position++] = entry;
        }

        @Override
        public void writeRaw(byte[] entry) {
            var length = entry.length;
            System.arraycopy(entry, 0, buffer, position, length);
            position += length;
        }

        @Override
        public void writeRaw(byte[] entry, int offset, int length) {
            System.arraycopy(entry, offset, buffer, position, length);
            position += length;
        }

        @Override
        public void writeRaw(ByteBuffer entry) {
            var length = entry.remaining();
            entry.get(entry.position(), buffer, position, length);
            position += length;
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
        public void writeRaw(byte entry) {
            buffer.put(entry);
        }

        @Override
        public void writeRaw(byte[] entry) {
            buffer.put(entry);
        }

        @Override
        public void writeRaw(byte[] entry, int offset, int length) {
            buffer.put(entry, offset, length);
        }

        @Override
        public void writeRaw(ByteBuffer entry) {
            buffer.put(entry);
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

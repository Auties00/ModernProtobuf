package it.auties.protobuf.stream;

import it.auties.protobuf.exception.ProtobufDeserializationException;
import it.auties.protobuf.exception.ProtobufSerializationException;
import it.auties.protobuf.model.ProtobufLazyString;
import it.auties.protobuf.model.ProtobufWireType;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * An abstract output stream for writing Protocol Buffer encoded data.
 * <p>
 * This class provides a comprehensive API for serializing Protocol Buffer messages to various
 * output destinations, including byte arrays, ByteBuffers, and OutputStreams.
 * </p>
 *
 * <h2>Size Calculation:</h2>
 * <p>
 * The class provides static utility methods for calculating the serialized size of various
 * field types before writing, which is crucial for performance.
 * </p>
 *
 * @param <OUTPUT> the type of output this stream produces (byte[], ByteBuffer, OutputStream, ...)
 * @implNote this class performs no checks on whether the data it's serializing is correct(ex. you can write an illegal field index/wire type).
 *           this choice was made because the annotation processor is expected to perform these checks at compile time.
 *           if you are using this class manually for any particular reason, keep this in mind.
 *
 * @see ProtobufInputStream
 */
@SuppressWarnings({"unused", "ForLoopReplaceableByForEach"})
public abstract class ProtobufOutputStream<OUTPUT> {
    private static final ThreadLocal<CharsetEncoder> UTF8_ENCODER = ThreadLocal.withInitial(StandardCharsets.UTF_8::newEncoder);

    public static int getPropertyWireTagSize(long fieldIndex, int wireType) {
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

    public static int getBytesSize(ByteBuffer value) {
        if(value == null) {
            return 0;
        } else {
            var length = value.remaining();
            return getVarIntSize(length) + length;
        }
    }

    public static int getBytesSize(byte[] value) {
        if(value == null) {
            return 0;
        } else {
            return getVarIntSize(value.length) + value.length;
        }
    }

    public static int getVarIntPackedSize(long fieldIndex, Collection<? extends Number> values) {
        if (values == null) {
            return 0;
        } else if(values instanceof List<? extends Number> list) {
            return getVarIntPackedSize(fieldIndex, list);
        } else {
            var valueSize = 0;
            for (var value : values) {
                if(value != null) {
                    valueSize += getVarIntSize(value.longValue());
                }
            }
            return getPropertyWireTagSize(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED)
                   + getVarIntSize(valueSize)
                   + valueSize;
        }
    }

    public static int getVarIntPackedSize(long fieldIndex, List<? extends Number> values) {
        if(values == null){
            return 0;
        }else {
            var valueSize = 0;
            for(int i = 0, length = values.size(); i < length; i++) {
                var value = values.get(i);
                if(value != null) {
                    valueSize += getVarIntSize(value.longValue());
                }
            }
            return getPropertyWireTagSize(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED)
                   + getVarIntSize(valueSize)
                   + valueSize;
        }
    }

    public static int getFixed32PackedSize(long fieldIndex, Collection<? extends Number> values) {
        if (values == null) {
            return 0;
        } else {
            var valuesSize = values.size() * Integer.BYTES;
            return getPropertyWireTagSize(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED)
                   + getVarIntSize(valuesSize)
                   + valuesSize;
        }
    }

    public static int getFixed64PackedSize(long fieldIndex, Collection<? extends Number> values) {
        if(values == null){
            return 0;
        }else {
            var valuesSize = values.size() * Long.BYTES;
            return getPropertyWireTagSize(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED)
                   + getVarIntSize(valuesSize)
                   + valuesSize;
        }
    }

    public static int getBoolPackedSize(long fieldIndex, Collection<Long> values) {
        if(values == null){
            return 0;
        }else {
            var valuesSize = values.size();
            return getPropertyWireTagSize(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED)
                   + getVarIntSize(valuesSize)
                   + valuesSize;
        }
    }

    public static ProtobufOutputStream<byte[]> toBytes(int length) {
        if(length < 0) {
            throw new IllegalArgumentException("length must not be negative");
        }else {
            return new ProtobufOutputStream.Bytes(new byte[length], 0);
        }
    }

    public static ProtobufOutputStream<byte[]> toBytes(byte[] bytes, int offset) {
        Objects.requireNonNull(bytes, "bytes must not be null");
        Objects.checkIndex(offset, bytes.length);
        return new ProtobufOutputStream.Bytes(bytes, offset);
    }

    public static ProtobufOutputStream<ByteBuffer> toBuffer(ByteBuffer buffer) {
        Objects.requireNonNull(buffer, "buffer must not be null");
        return new ProtobufOutputStream.Buffer(buffer);
    }

    public static ProtobufOutputStream<OutputStream> toStream(OutputStream buffer) {
        Objects.requireNonNull(buffer, "buffer must not be null");
        return new ProtobufOutputStream.Stream(buffer);
    }

    protected long fieldIndex;
    protected ProtobufOutputStream() {
        resetFieldIndex();
    }

    public void writeStartGroupProperty(long fieldIndex) {
        writePropertyTag(fieldIndex, ProtobufWireType.WIRE_TYPE_START_OBJECT);
    }

    public void writeEndGroupProperty(long fieldIndex) {
        writePropertyTag(fieldIndex, ProtobufWireType.WIRE_TYPE_END_OBJECT);
    }

    public void writeInt32Property(long fieldIndex, Integer value) {
        if(value != null){
            writeInt32Property(fieldIndex, (int) value);
        }
    }

    public void writeInt32Property(long fieldIndex, int value) {
        writePropertyTag(fieldIndex, ProtobufWireType.WIRE_TYPE_VAR_INT);
        writeRawVarInt32(value);
    }

    public void writeInt32PackedProperty(long fieldIndex, Collection<? extends Integer> values) {
        writeVarInt32PackedProperty(fieldIndex, values);
    }

    public void writeInt32PackedProperty(long fieldIndex, List<? extends Integer> values) {
        writeVarInt32PackedProperty(fieldIndex, values);
    }

    public void writeUInt32Property(long fieldIndex, Integer value) {
        if(value != null){
            writeUInt32Property(fieldIndex, (int) value);
        }
    }

    public void writeUInt32Property(long fieldIndex, int value) {
        writePropertyTag(fieldIndex, ProtobufWireType.WIRE_TYPE_VAR_INT);
        writeRawVarInt32(value);
    }

    private void writeUInt32PackedProperty(long fieldIndex, Collection<? extends Integer> values) {
        writeVarInt32PackedProperty(fieldIndex, values);
    }

    private void writeUInt32PackedProperty(long fieldIndex, List<? extends Integer> values) {
        writeVarInt32PackedProperty(fieldIndex, values);
    }

    public void writeFloatProperty(long fieldIndex, Float value) {
        if(value != null){
            writeFloatProperty(fieldIndex, (float) value);
        }
    }

    public void writeFloatProperty(long fieldIndex, float value) {
        writeFixed32Property(fieldIndex, Float.floatToRawIntBits(value));
    }

    public void writeFloatPackedProperty(long fieldIndex, Collection<? extends Float> values) {
        if (values instanceof List<? extends Float> list) {
            writeFloatPackedProperty(fieldIndex, list);
        }else if(values != null){
            var size = 0;
            for (var value : values) {
                if(value != null) {
                    size += Float.BYTES;
                }
            }
            writePropertyTag(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeRawVarInt32(size);
            for (var value : values) {
                if(value != null) {
                    writeRawVarInt32(Float.floatToRawIntBits(value));
                }
            }
        }
    }

    public void writeFloatPackedProperty(long fieldIndex, List<? extends Float> values) {
        if(values != null){
            var size = 0;
            for (int i = 0, n = values.size(); i < n; i++) {
                var value = values.get(i);
                if(value != null) {
                    size += Float.BYTES;
                }
            }
            writePropertyTag(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeRawVarInt32(size);
            for (int i = 0, n = values.size(); i < n; i++) {
                var value = values.get(i);
                if(value != null) {
                    writeRawVarInt32(Float.floatToRawIntBits(value));
                }
            }
        }
    }

    public void writeFixed32Property(long fieldIndex, Integer value) {
        if(value != null){
            writeFixed32Property(fieldIndex, (int) value);
        }
    }

    public void writeFixed32Property(long fieldIndex, int value) {
        writePropertyTag(fieldIndex, ProtobufWireType.WIRE_TYPE_FIXED32);
        writeRawVarInt32(value);
    }

    public void writeFixed32PackedProperty(long fieldIndex, Collection<? extends Integer> values) {
        if (values instanceof List<? extends Integer> list) {
            writeFixed32PackedProperty(fieldIndex, list);
        }else if(values != null){
            var size = 0;
            for (var value : values) {
                if(value != null) {
                    size += Integer.BYTES;
                }
            }
            writePropertyTag(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeRawVarInt32(size);
            for (var value : values) {
                if(value != null) {
                    writeRawVarInt32(value);
                }
            }
        }
    }

    public void writeFixed32PackedProperty(long fieldIndex, List<? extends Integer> values) {
        if(values != null){
            var size = 0;
            for (int i = 0, n = values.size(); i < n; i++) {
                var value = values.get(i);
                if(value != null) {
                    size += Integer.BYTES;
                }
            }
            writePropertyTag(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeRawVarInt32(size);
            for (int i = 0, n = values.size(); i < n; i++) {
                var value = values.get(i);
                if(value != null) {
                    writeRawVarInt32(value);
                }
            }
        }
    }

    public void writeInt64Property(long fieldIndex, Long value) {
        if(value != null){
            writeInt64Property(fieldIndex, (long) value);
        }
    }

    public void writeInt64Property(long fieldIndex, long value) {
        writeUInt64Property(fieldIndex, value);
    }

    public void writeInt64PackedProperty(long fieldIndex, Collection<? extends Long> values) {
        writeVarInt64PackedProperty(fieldIndex, values);
    }

    public void writeInt64PackedProperty(long fieldIndex, List<? extends Long> values) {
        writeVarInt64PackedProperty(fieldIndex, values);
    }

    public void writeUInt64Property(long fieldIndex, Long value) {
        if(value != null){
            writeUInt64Property(fieldIndex, (long) value);
        }
    }

    public void writeUInt64Property(long fieldIndex, long value) {
        writePropertyTag(fieldIndex, ProtobufWireType.WIRE_TYPE_VAR_INT);
        writeRawVarInt64(value);
    }

    public void writeUInt64PackedProperty(long fieldIndex, Collection<? extends Long> values) {
        writeVarInt64PackedProperty(fieldIndex, values);
    }

    public void writeUInt64PackedProperty(long fieldIndex, List<? extends Long> values) {
        writeVarInt64PackedProperty(fieldIndex, values);
    }
    
    public void writeDoubleProperty(long fieldIndex, Double value) {
        if(value != null){
            writeDoubleProperty(fieldIndex, (double) value);
        }
    }

    public void writeDoubleProperty(long fieldIndex, double value) {
        writeFixed64Property(fieldIndex, Double.doubleToRawLongBits(value));
    }

    public void writeDoublePackedProperty(long fieldIndex, Collection<? extends Double> values) {
        if (values instanceof List<? extends Double> list) {
            writeDoublePackedProperty(fieldIndex, list);
        } else if(values != null) {
            var size = 0;
            for (var value : values) {
                if(value != null) {
                    size += Double.BYTES;
                }
            }
            writePropertyTag(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeRawVarInt32(size);
            for (var value : values) {
                if(value != null) {
                    writeRawVarInt64(Double.doubleToRawLongBits(value));
                }
            }
        }
    }

    public void writeDoublePackedProperty(long fieldIndex, List<? extends Double> values) {
        if(values != null) {
            var size = 0;
            for (int i = 0, n = values.size(); i < n; i++) {
                var value = values.get(i);
                if(value != null) {
                    size += Double.BYTES;
                }
            }
            writePropertyTag(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeRawVarInt32(size);
            for (int i = 0, n = values.size(); i < n; i++) {
                var value = values.get(i);
                if(value != null) {
                    writeRawVarInt64(Double.doubleToRawLongBits(value));
                }
            }
        }
    }

    public void writeFixed64Property(long fieldIndex, Long value) {
        if(value != null){
            writeFixed64Property(fieldIndex, (long) value);
        }
    }

    public void writeFixed64Property(long fieldIndex, long value) {
        writePropertyTag(fieldIndex, ProtobufWireType.WIRE_TYPE_FIXED64);
        writeRawVarInt64(value);
    }

    public void writeFixed64PackedProperty(long fieldIndex, Collection<? extends Long> values) {
        if (values instanceof List<? extends Long> list) {
            writeFixed64PackedProperty(fieldIndex, list);
        }else if(values != null) {
            var size = 0;
            for (var value : values) {
                if(value != null) {
                    size += Long.BYTES;
                }
            }
            writePropertyTag(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeRawVarInt32(size);
            for (var value : values) {
                if(value != null) {
                    writeRawVarInt64(value);
                }
            }
        }
    }

    public void writeFixed64PackedProperty(long fieldIndex, List<? extends Long> values) {
        if(values != null){
            var size = 0;
            for (int i = 0, n = values.size(); i < n; i++) {
                var value = values.get(i);
                if(value != null) {
                    size += Long.BYTES;
                }
            }
            writePropertyTag(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeRawVarInt32(size);
            for (int i = 0, n = values.size(); i < n; i++) {
                var value = values.get(i);
                if(value != null) {
                    writeRawVarInt64(value);
                }
            }
        }
    }

    public void writeBoolProperty(long fieldIndex, Boolean value) {
        if(value != null){
            writeBoolProperty(fieldIndex, (boolean) value);
        }
    }

    public void writeBoolProperty(long fieldIndex, boolean value) {
        writePropertyTag(fieldIndex, ProtobufWireType.WIRE_TYPE_VAR_INT);
        writeRawByte((byte) (value ? 1 : 0));
    }

    public void writeBoolPackedProperty(long fieldIndex, Collection<? extends Boolean> values) {
        if (values instanceof List<? extends Boolean> list) {
            writeBoolPackedProperty(fieldIndex, list);
        }else if(values != null) {
            var size = 0;
            for (var value : values) {
                if(value != null) {
                    size++;
                }
            }
            writePropertyTag(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeRawVarInt32(size);
            for (var value : values) {
                if(value != null) {
                    writeRawByte((byte) (value ? 1 : 0));
                }
            }
        }
    }

    public void writeBoolPackedProperty(long fieldIndex, List<? extends Boolean> values) {
        if(values != null){
            var size = 0;
            for (int i = 0, n = values.size(); i < n; i++) {
                var value = values.get(i);
                if(value != null) {
                    size++;
                }
            }
            writePropertyTag(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeRawVarInt32(size);
            for (int i = 0, n = values.size(); i < n; i++) {
                var value = values.get(i);
                if(value != null) {
                    writeRawByte((byte) (value ? 1 : 0));
                }
            }
        }
    }

    public void writeLengthDelimitedPropertyLength(int length) {
        if(length < 0) {
            throw ProtobufDeserializationException.negativeLength(length);
        } else {
            writeRawVarInt32(length);
        }
    }

    public void writeLengthDelimitedProperty(long fieldIndex, String value) {
        if(value != null) {
            writePropertyTag(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeRawDecodedString(value);
        }
    }

    public void writeLengthDelimitedProperty(long fieldIndex, ProtobufLazyString value) {
        if(value != null) {
            value.writeTo(fieldIndex, this);
        }
    }

    public void writeLengthDelimitedProperty(long fieldIndex, ByteBuffer value) {
        if(value != null){
            writePropertyTag(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            var size = value.remaining();
            writeRawVarInt32(size);
            writeRawBuffer(value);
        }
    }

    public void writeLengthDelimitedProperty(long fieldIndex, byte[] value) {
        writeLengthDelimitedProperty(fieldIndex, value, 0, value.length);
    }

    public void writeLengthDelimitedProperty(long fieldIndex, byte[] value, int offset, int size) {
        if(value != null){
            writePropertyTag(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeRawVarInt32(size);
            writeRawBytes(value, offset, size);
        }
    }

    public void writeMessageProperty(long fieldIndex, int size) {
        writePropertyTag(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeRawVarInt32(size);
    }

    public void preparePropertyTag(long fieldIndex) {
        this.fieldIndex = fieldIndex;
    }

    public void writePropertyTag(int wireType) {
        if(fieldIndex == Long.MIN_VALUE) {
            throw new IllegalStateException("No field index was set");
        }else {
            writeRawVarInt64(ProtobufWireType.makeTag(fieldIndex, wireType));
            resetFieldIndex();
        }
    } 
    
    public void writePropertyTag(long fieldIndex, int wireType) {
        writeRawVarInt64(ProtobufWireType.makeTag(fieldIndex, wireType));
        resetFieldIndex();
    }

    private void resetFieldIndex() {
        this.fieldIndex = Long.MIN_VALUE;
    }

    public abstract void writeRawVarInt32(int value);
    public abstract void writeRawVarInt64(long value);
    public abstract void writeRawByte(byte entry);
    public abstract void writeRawBytes(byte[] entry);
    public abstract void writeRawBytes(byte[] entry, int offset, int length);
    public abstract void writeRawBuffer(ByteBuffer entry);
    public abstract void writeRawDecodedString(String value);
    public abstract OUTPUT toOutput();

    private void writeVarInt32PackedProperty(long fieldIndex, Collection<? extends Integer> values) {
        if (values instanceof List<? extends Integer> list) {
            writeVarInt32PackedProperty(fieldIndex, list);
        } else if(values != null) {
            var size = 0;
            for (var value : values) {
                if(value != null) {
                    size += getVarIntSize(value);
                }
            }
            writePropertyTag(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeRawVarInt32(size);
            for (var value : values) {
                if(value != null) {
                    writeRawVarInt32(value);
                }
            }
        }
    }

    private void writeVarInt32PackedProperty(long fieldIndex, List<? extends Integer> values) {
        if(values != null){
            var size = 0;
            for (int i = 0, n = values.size(); i < n; i++) {
                var value = values.get(i);
                if(value != null) {
                    size += getVarIntSize(value);
                }
            }
            writePropertyTag(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeRawVarInt32(size);
            for (int i = 0, n = values.size(); i < n; i++) {
                var value = values.get(i);
                if(value != null) {
                    writeRawVarInt32(value);
                }
            }
        }
    }

    private void writeVarInt64PackedProperty(long fieldIndex, Collection<? extends Long> values) {
        if (values instanceof List<? extends Long> list) {
            writeVarInt64PackedProperty(fieldIndex, list);
        } else if(values != null) {
            var size = 0;
            for (var value : values) {
                if(value != null) {
                    size += getVarIntSize(value);
                }
            }
            writePropertyTag(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeRawVarInt32(size);
            for (var value : values) {
                if(value != null) {
                    writeRawVarInt64(value);
                }
            }
        }
    }

    private void writeVarInt64PackedProperty(long fieldIndex, List<? extends Long> values) {
        if(values != null){
            var size = 0;
            for (int i = 0, n = values.size(); i < n; i++) {
                var value = values.get(i);
                if(value != null) {
                    size += getVarIntSize(value);
                }
            }
            writePropertyTag(fieldIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeRawVarInt32(size);
            for (int i = 0, n = values.size(); i < n; i++) {
                var value = values.get(i);
                if(value != null) {
                    writeRawVarInt64(value);
                }
            }
        }
    }

    private static final class Stream extends ProtobufOutputStream<OutputStream> {
        private final OutputStream outputStream;
        private byte[] tempBuffer;

        private Stream(OutputStream outputStream) {
            this.outputStream = outputStream;
        }

        @Override
        public void writeRawByte(byte entry) {
            try {
                outputStream.write(entry);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void writeRawBytes(byte[] entry) {
            try {
                outputStream.write(entry);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void writeRawBytes(byte[] entry, int offset, int length) {
            try {
                outputStream.write(entry, offset, length);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void writeRawBuffer(ByteBuffer entry) {
            try {
                if(entry.hasArray()) {
                    outputStream.write(entry.array(), entry.arrayOffset() + entry.position(), entry.remaining());
                } else {
                    var size = entry.remaining();
                    if (tempBuffer == null || tempBuffer.length < size) {
                        tempBuffer = new byte[size];
                    }
                    entry.get(entry.position(), tempBuffer, 0, size);
                    outputStream.write(tempBuffer, 0, size);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void writeRawDecodedString(String value) {
            try {
                outputStream.write(value.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void writeRawVarInt32(int value) {
            try {
                outputStream.write(value & 0xFF);
                outputStream.write((value >> 8) & 0xFF);
                outputStream.write((value >> 16) & 0xFF);
                outputStream.write((value >> 24) & 0xFF);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void writeRawVarInt64(long value) {
            try {
                outputStream.write((int) value & 0xFF);
                outputStream.write((int) (value >> 8) & 0xFF);
                outputStream.write((int) (value >> 16) & 0xFF);
                outputStream.write((int) (value >> 24) & 0xFF);
                outputStream.write((int) (value >> 32) & 0xFF);
                outputStream.write((int) (value >> 40) & 0xFF);
                outputStream.write((int) (value >> 48) & 0xFF);
                outputStream.write((int) (value >> 56) & 0xFF);
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
        public void writeRawByte(byte entry) {
            buffer[position++] = entry;
        }

        @Override
        public void writeRawBytes(byte[] entry) {
            var length = entry.length;
            System.arraycopy(entry, 0, buffer, position, length);
            position += length;
        }

        @Override
        public void writeRawBytes(byte[] entry, int offset, int length) {
            System.arraycopy(entry, offset, buffer, position, length);
            position += length;
        }

        @Override
        public void writeRawBuffer(ByteBuffer entry) {
            var length = entry.remaining();
            entry.get(entry.position(), buffer, position, length);
            position += length;
        }

        @Override
        public void writeRawDecodedString(String value) {
            var encoder = UTF8_ENCODER.get();
            encoder.reset();
            var target = ByteBuffer.wrap(buffer, position, buffer.length - position);
            var result = encoder.encode(CharBuffer.wrap(value), target, true);
            if (!result.isError()) {
                position = target.position();
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
        public void writeRawVarInt32(int value) {
            buffer[position++] = (byte) (value & 0xFF);
            buffer[position++] = (byte) ((value >> 8) & 0xFF);
            buffer[position++] = (byte) ((value >> 16) & 0xFF);
            buffer[position++] = (byte) ((value >> 24) & 0xFF);
        }

        @Override
        public void writeRawVarInt64(long value) {
            buffer[position++] = (byte) ((int) value & 0xFF);
            buffer[position++] = (byte) ((int) (value >> 8) & 0xFF);
            buffer[position++] = (byte) ((int) (value >> 16) & 0xFF);
            buffer[position++] = (byte) ((int) (value >> 24) & 0xFF);
            buffer[position++] = (byte) ((int) (value >> 32) & 0xFF);
            buffer[position++] = (byte) ((int) (value >> 40) & 0xFF);
            buffer[position++] = (byte) ((int) (value >> 48) & 0xFF);
            buffer[position++] = (byte) ((int) (value >> 56) & 0xFF);
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
        public void writeRawByte(byte entry) {
            buffer.put(entry);
        }

        @Override
        public void writeRawBytes(byte[] entry) {
            buffer.put(entry);
        }

        @Override
        public void writeRawBytes(byte[] entry, int offset, int length) {
            buffer.put(entry, offset, length);
        }

        @Override
        public void writeRawBuffer(ByteBuffer entry) {
            buffer.put(entry);
        }

        @Override
        public void writeRawDecodedString(String value) {
            var encoder = UTF8_ENCODER.get();
            encoder.reset();
            var result = encoder.encode(CharBuffer.wrap(value), buffer, true);
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
        public void writeRawVarInt32(int value) {
            buffer.put((byte) (value & 0xFF));
            buffer.put((byte) ((value >> 8) & 0xFF));
            buffer.put((byte) ((value >> 16) & 0xFF));
            buffer.put((byte) ((value >> 24) & 0xFF));
        }

        @Override
        public void writeRawVarInt64(long value) {
            buffer.put((byte) ((int) value & 0xFF));
            buffer.put((byte) ((int) (value >> 8) & 0xFF));
            buffer.put((byte) ((int) (value >> 16) & 0xFF));
            buffer.put((byte) ((int) (value >> 24) & 0xFF));
            buffer.put((byte) ((int) (value >> 32) & 0xFF));
            buffer.put((byte) ((int) (value >> 40) & 0xFF));
            buffer.put((byte) ((int) (value >> 48) & 0xFF));
            buffer.put((byte) ((int) (value >> 56) & 0xFF));
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
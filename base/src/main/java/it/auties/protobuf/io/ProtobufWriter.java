package it.auties.protobuf.io;

import it.auties.protobuf.exception.ProtobufDeserializationException;
import it.auties.protobuf.exception.ProtobufSerializationException;
import it.auties.protobuf.model.ProtobufUnknownValue;
import it.auties.protobuf.model.ProtobufWireType;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
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
 * @see ProtobufReader
 */
public abstract non-sealed class ProtobufWriter<OUTPUT> extends ProtobufIO {
    public static ProtobufWriter<byte[]> toBytes(int length) {
        if(length < 0) {
            throw new IllegalArgumentException("length must not be negative");
        }else {
            return new ByteArrayWriter(new byte[length], 0);
        }
    }

    public static ProtobufWriter<byte[]> toBytes(byte[] bytes, int offset) {
        Objects.requireNonNull(bytes, "bytes must not be null");
        Objects.checkIndex(offset, bytes.length);
        return new ByteArrayWriter(bytes, offset);
    }

    public static ProtobufWriter<ByteBuffer> toHeapBuffer(int length) {
        if(length < 0) {
            throw new IllegalArgumentException("length must not be negative");
        }else {
            return new ByteBufferWriter(ByteBuffer.allocate(length));
        }
    }

    public static ProtobufWriter<ByteBuffer> toDirectBuffer(int length) {
        if(length < 0) {
            throw new IllegalArgumentException("length must not be negative");
        }else {
            return new ByteBufferWriter(ByteBuffer.allocateDirect(length));
        }
    }

    public static ProtobufWriter<ByteBuffer> toBuffer(ByteBuffer buffer) {
        Objects.requireNonNull(buffer, "buffer must not be null");
        if(buffer.isReadOnly()) {
            throw new IllegalArgumentException("buffer is read-only");
        } else {
            return new ByteBufferWriter(buffer);
        }
    }

    public static ProtobufWriter<MemorySegment> toMemorySegment(MemorySegment segment) {
        Objects.requireNonNull(segment, "segment must not be null");
        return new MemorySegmentWriter(segment);
    }

    public static ProtobufWriter<OutputStream> toStream(OutputStream buffer) {
        Objects.requireNonNull(buffer, "buffer must not be null");
        return new InputStreamWriter(buffer);
    }

    protected long propertyIndex;
    protected ProtobufWriter() {
        resetPropertyTag();
    }

    public boolean hasPropertyIndex() {
        return propertyIndex != Long.MIN_VALUE;
    }
    
    public void preparePropertyTag(long propertyIndex) {
        this.propertyIndex = propertyIndex;
    }

    public void writePropertyTag(int wireType) {
        if(propertyIndex == Long.MIN_VALUE) {
            throw new IllegalStateException("No field index was set");
        }else {
            writeRawFixedInt64(ProtobufWireType.makeTag(propertyIndex, wireType));
            resetPropertyTag();
        }
    }

    public void writePropertyTag(long propertyIndex, int wireType) {
        writeRawFixedInt64(ProtobufWireType.makeTag(propertyIndex, wireType));
        resetPropertyTag();
    }

    private void resetPropertyTag() {
        this.propertyIndex = Long.MIN_VALUE;
    }
    
    public void writeLengthDelimitedPropertyLength(int length) {
        if(length < 0) {
            throw ProtobufDeserializationException.negativeLength(length);
        } else {
            writeRawFixedInt32(length);
        }
    }

    public void writeStartGroupProperty(long propertyIndex) {
        writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_START_OBJECT);
    }

    public void writeEndGroupProperty(long propertyIndex) {
        writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_END_OBJECT);
    }

    public void writeFloatProperty(long propertyIndex, Float value) {
        if(value != null){
            writeFloatProperty(propertyIndex, (float) value);
        }
    }

    public void writeFloatProperty(long propertyIndex, float value) {
        writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_FIXED32);
        writeRawFloat(value);
    }

    public void writeDoubleProperty(long propertyIndex, Double value) {
        if(value != null){
            writeDoubleProperty(propertyIndex, (double) value);
        }
    }

    public void writeDoubleProperty(long propertyIndex, double value) {
        writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_FIXED64);
        writeRawDouble(value);
    }

    public void writeBoolProperty(long propertyIndex, Boolean value) {
        if(value != null){
            writeBoolProperty(propertyIndex, (boolean) value);
        }
    }

    public void writeBoolProperty(long propertyIndex, boolean value) {
        writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_VAR_INT);
        writeRawByte((byte) (value ? 1 : 0));
    }

    
    public void writeInt32Property(long propertyIndex, Integer value) {
        if(value != null){
            writeInt32Property(propertyIndex, (int) value);
        }
    }

    public void writeInt32Property(long propertyIndex, int value) {
        writeVarInt32(propertyIndex, value);
    }

    public void writeUInt32Property(long propertyIndex, Integer value) {
        if(value != null){
            writeUInt32Property(propertyIndex, (int) value);
        }
    }

    public void writeUInt32Property(long propertyIndex, int value) {
        writeVarInt32(propertyIndex, value);
    }

    private void writeVarInt32(long propertyIndex, int value) {
        writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_VAR_INT);
        writeRawVarInt32(value);
    }

    public void writeSInt32Property(long propertyIndex, Integer value) {
        if(value != null){
            writeSInt32Property(propertyIndex, (int) value);
        }
    }

    public void writeSInt32Property(long propertyIndex, int value) {
        writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_VAR_INT);
        writeRawZigZagVarInt32(value);
    }


    public void writeInt64Property(long propertyIndex, Long value) {
        if(value != null){
            writeInt64Property(propertyIndex, (long) value);
        }
    }

    public void writeInt64Property(long propertyIndex, long value) {
        writeVarInt64(propertyIndex, value);
    }

    public void writeUInt64Property(long propertyIndex, Long value) {
        if(value != null){
            writeUInt64Property(propertyIndex, (long) value);
        }
    }

    public void writeUInt64Property(long propertyIndex, long value) {
        writeVarInt64(propertyIndex, value);
    }

    private void writeVarInt64(long propertyIndex, long value) {
        writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_VAR_INT);
        writeRawVarInt64(value);
    }


    public void writeSInt64Property(long propertyIndex, Long value) {
        if(value != null){
            writeSInt64Property(propertyIndex, (long) value);
        }
    }

    public void writeSInt64Property(long propertyIndex, long value) {
        writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_VAR_INT);
        writeRawZigZagVarInt64(value);
    }

    public void writeFixed32Property(long propertyIndex, Integer value) {
        if(value != null){
            writeFixed32(propertyIndex, value);
        }
    }

    public void writeFixed32Property(long propertyIndex, int value) {
        writeFixed32(propertyIndex, value);
    }

    public void writeSFixed32Property(long propertyIndex, Integer value) {
        if(value != null){
            writeFixed32(propertyIndex, value);
        }
    }

    public void writeSFixed32Property(long propertyIndex, int value) {
        writeFixed32(propertyIndex, value);
    }
    
    private void writeFixed32(long propertyIndex, int value) {
        writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_FIXED32);
        writeRawFixedInt32(value);
    }

    public void writeFixed64Property(long propertyIndex, Long value) {
        if(value != null){
            writeFixed64(propertyIndex, value);
        }
    }

    public void writeFixed64Property(long propertyIndex, long value) {
        writeFixed64(propertyIndex, value);
    }

    public void writeSFixed64Property(long propertyIndex, Long value) {
        if(value != null){
            writeFixed64(propertyIndex, value);
        }
    }

    public void writeSFixed64Property(long propertyIndex, long value) {
        writeFixed64(propertyIndex, value);
    }

    private void writeFixed64(long propertyIndex, long value) {
        writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_FIXED32);
        writeRawFixedInt64(value);
    }

    private void writeUnknownProperty(long propertyIndex, ProtobufUnknownValue value) {
        switch (value) {
            case ProtobufUnknownValue.Fixed32(var fixed32Value) -> writeFixed32(propertyIndex, fixed32Value);

            case ProtobufUnknownValue.Fixed64(var fixed64Value) -> writeFixed64(propertyIndex, fixed64Value);

            case ProtobufUnknownValue.Group(var groupValue) -> {
                writeStartGroupProperty(propertyIndex);
                for(var entry : groupValue.entrySet()) {
                    writeUnknownProperty(entry.getKey(), entry.getValue());
                }
                writeEndGroupProperty(propertyIndex);
            }

            case ProtobufUnknownValue.LengthDelimited lengthDelimited -> {
                switch (lengthDelimited) {
                    case ProtobufUnknownValue.LengthDelimited.AsByteArray(var lengthDelimitedAsByteArray) -> {
                        writeLengthDelimitedPropertyLength(lengthDelimitedAsByteArray.length);
                        writeRawBytes(lengthDelimitedAsByteArray);
                    }

                    case ProtobufUnknownValue.LengthDelimited.AsByteBuffer(var lengthDelimitedAsByteBuffer) -> {
                        writeLengthDelimitedPropertyLength(lengthDelimitedAsByteBuffer.remaining());
                        writeRawBuffer(lengthDelimitedAsByteBuffer);
                    }

                    case ProtobufUnknownValue.LengthDelimited.AsMemorySegment(var lengthDelimitedAsMemorySegment) -> {
                        writeLengthDelimitedPropertyLength((int) lengthDelimitedAsMemorySegment.byteSize());
                        writeRawMemorySegment(lengthDelimitedAsMemorySegment);
                    }
                }
            }

            case ProtobufUnknownValue.VarInt(var varIntValue) -> writeInt64Property(propertyIndex, varIntValue);

            case null -> { /* Having this branch prevents a NPE */ }
        }
    }
    
    public void writePackedFloatProperty(long propertyIndex, float[] values) {
        if(values != null){
            var size = values.length * Float.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawFloat(value);
            }
        }
    }

    public void writePackedDoubleProperty(long propertyIndex, double[] values) {
        if(values != null){
            var size = values.length * Double.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawDouble(value);
            }
        }
    }

    public void writePackedInt32Property(long propertyIndex, byte[] values) {
        if(values != null){
            var size = values.length * Integer.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawVarInt32(value);
            }
        }
    }

    public void writePackedInt32Property(long propertyIndex, short[] values) {
        if(values != null){
            var size = values.length * Integer.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawVarInt32(value);
            }
        }
    }
    
    public void writePackedInt32Property(long propertyIndex, int[] values) {
        if(values != null){
            var size = values.length * Integer.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawVarInt32(value);
            }
        }
    }
    
    public void writePackedUInt32Property(long propertyIndex, byte[] values) {
        if(values != null){
            var size = values.length * Integer.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawVarInt32(value);
            }
        }
    }

    public void writePackedUInt32Property(long propertyIndex, short[] values) {
        if(values != null){
            var size = values.length * Integer.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawVarInt32(value);
            }
        }
    }

    public void writePackedUInt32Property(long propertyIndex, int[] values) {
        if(values != null){
            var size = values.length * Integer.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawVarInt32(value);
            }
        }
    }
    
    public void writePackedSInt32Property(long propertyIndex, byte[] values) {
        if(values != null){
            var size = values.length * Integer.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawZigZagVarInt32(value);
            }
        }
    }

    public void writePackedSInt32Property(long propertyIndex, short[] values) {
        if(values != null){
            var size = values.length * Integer.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawZigZagVarInt32(value);
            }
        }
    }

    public void writePackedSInt32Property(long propertyIndex, int[] values) {
        if(values != null){
            var size = values.length * Integer.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawZigZagVarInt32(value);
            }
        }
    }
    
    public void writePackedInt64Property(long propertyIndex, byte[] values) {
        if(values != null){
            var size = values.length * Long.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawVarInt64(value);
            }
        }
    }

    public void writePackedInt64Property(long propertyIndex, short[] values) {
        if(values != null){
            var size = values.length * Long.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawVarInt64(value);
            }
        }
    }

    public void writePackedInt64Property(long propertyIndex, int[] values) {
        if(values != null){
            var size = values.length * Long.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawVarInt64(value);
            }
        }
    }


    public void writePackedInt64Property(long propertyIndex, long[] values) {
        if(values != null){
            var size = values.length * Long.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawVarInt64(value);
            }
        }
    }

    public void writePackedUInt64Property(long propertyIndex, byte[] values) {
        if(values != null){
            var size = values.length * Long.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawVarInt64(value);
            }
        }
    }

    public void writePackedUInt64Property(long propertyIndex, short[] values) {
        if(values != null){
            var size = values.length * Long.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawVarInt64(value);
            }
        }
    }

    public void writePackedUInt64Property(long propertyIndex, int[] values) {
        if(values != null){
            var size = values.length * Long.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawVarInt64(value);
            }
        }
    }

    public void writePackedUInt64Property(long propertyIndex, long[] values) {
        if(values != null){
            var size = values.length * Long.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawVarInt64(value);
            }
        }
    }

    public void writePackedSInt64Property(long propertyIndex, byte[] values) {
        if(values != null){
            var size = values.length * Long.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawZigZagVarInt64(value);
            }
        }
    }

    public void writePackedSInt64Property(long propertyIndex, short[] values) {
        if(values != null){
            var size = values.length * Long.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawZigZagVarInt64(value);
            }
        }
    }

    public void writePackedSInt64Property(long propertyIndex, int[] values) {
        if(values != null){
            var size = values.length * Long.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawZigZagVarInt64(value);
            }
        }
    }

    public void writePackedSInt64Property(long propertyIndex, long[] values) {
        if(values != null){
            var size = values.length * Long.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawZigZagVarInt64(value);
            }
        }
    }
    
    public void writePackedFixed32Property(long propertyIndex, byte[] values) {
        if(values != null){
            var size = values.length * Integer.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawFixedInt32(value);
            }
        }
    }

    public void writePackedFixed32Property(long propertyIndex, short[] values) {
        if(values != null){
            var size = values.length * Integer.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawFixedInt32(value);
            }
        }
    }

    public void writePackedFixed32Property(long propertyIndex, int[] values) {
        if(values != null){
            var size = values.length * Integer.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawFixedInt32(value);
            }
        }
    }

    public void writePackedSFixed32Property(long propertyIndex, byte[] values) {
        if(values != null){
            var size = values.length * Integer.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawFixedInt32(value);
            }
        }
    }

    public void writePackedSFixed32Property(long propertyIndex, short[] values) {
        if(values != null){
            var size = values.length * Integer.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawFixedInt32(value);
            }
        }
    }

    public void writePackedSFixed32Property(long propertyIndex, int[] values) {
        if(values != null){
            var size = values.length * Integer.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawFixedInt32(value);
            }
        }
    }

    public void writePackedFixed64Property(long propertyIndex, byte[] values) {
        if(values != null){
            var size = values.length * Long.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawFixedInt64(value);
            }
        }
    }

    public void writePackedFixed64Property(long propertyIndex, short[] values) {
        if(values != null){
            var size = values.length * Long.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawFixedInt64(value);
            }
        }
    }

    public void writePackedFixed64Property(long propertyIndex, int[] values) {
        if(values != null){
            var size = values.length * Long.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawFixedInt64(value);
            }
        }
    }

    public void writePackedFixed64Property(long propertyIndex, long[] values) {
        if(values != null){
            var size = values.length * Long.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawFixedInt64(value);
            }
        }
    }

    public void writePackedSFixed64Property(long propertyIndex, byte[] values) {
        if(values != null){
            var size = values.length * Long.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawFixedInt64(value);
            }
        }
    }

    public void writePackedSFixed64Property(long propertyIndex, short[] values) {
        if(values != null){
            var size = values.length * Long.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawFixedInt64(value);
            }
        }
    }

    public void writePackedSFixed64Property(long propertyIndex, int[] values) {
        if(values != null){
            var size = values.length * Long.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawFixedInt64(value);
            }
        }
    }

    public void writePackedSFixed64Property(long propertyIndex, long[] values) {
        if(values != null){
            var size = values.length * Long.BYTES;
            writePropertyTag(propertyIndex, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
            writeLengthDelimitedPropertyLength(size);
            for (var value : values) {
                writeRawFixedInt64(value);
            }
        }
    }

    public abstract void writeRawByte(byte entry);

    public abstract void writeRawBytes(byte[] entry, int offset, int length);
    public abstract void writeRawBuffer(ByteBuffer entry);
    public abstract void writeRawMemorySegment(MemorySegment entry);
    
    public abstract void writeRawFixedInt32(int entry);
    public abstract void writeRawFixedInt64(long entry);
    public abstract void writeRawFloat(float entry);
    public abstract void writeRawDouble(double entry);
    public abstract void writeRawVarInt32(int entry);
    public abstract void writeRawVarInt64(long entry);

    public void writeRawBytes(byte[] entry) {
        writeRawBytes(entry, 0, entry.length);
    }

    public void writeRawZigZagVarInt32(int value) {
        var zigzag = (value << 1) ^ (value >> 31);
        writeRawVarInt32(zigzag);
    }

    public void writeRawZigZagVarInt64(long value) {
        var zigzag = (value << 1) ^ (value >> 63);
        writeRawVarInt64(zigzag);
    }
    
    public abstract OUTPUT toOutput();

    private static final class ByteArrayWriter extends ProtobufWriter<byte[]> {
        private final byte[] buffer;
        private int position;

        private ByteArrayWriter(byte[] buffer, int offset) {
            this.buffer = buffer;
            this.position = offset;
        }

        @Override
        public void writeRawByte(byte entry) {
            try {
                buffer[position++] = entry;
            } catch (ArrayIndexOutOfBoundsException _) {
                throw ProtobufSerializationException.underflow();
            }
        }

        @Override
        public void writeRawBytes(byte[] entry, int offset, int length) {
            try {
                System.arraycopy(entry, offset, buffer, position, length);
                position += length;
            } catch (NegativeArraySizeException _) {
                throw ProtobufSerializationException.negativeLength();
            } catch (IndexOutOfBoundsException error) {
                throw ProtobufSerializationException.underflow();
            }
        }

        @Override
        public void writeRawBuffer(ByteBuffer entry) {
            var length = entry.remaining();
            entry.get(entry.position(), buffer, position, length);
            position += length;
        }

        @Override
        public void writeRawMemorySegment(MemorySegment entry) {
            var length = (int) entry.byteSize();
            if(entry.byteSize() != length) {
                throw ProtobufSerializationException.underflow();
            }else {
                try {
                    MemorySegment.copy(
                            entry,
                            ValueLayout.JAVA_BYTE,
                            0,
                            buffer,
                            position,
                            length
                    );
                    position += length;
                } catch (IndexOutOfBoundsException error) {
                    throw ProtobufSerializationException.underflow();
                }
            }
        }

        @Override
        public void writeRawFixedInt32(int value) {
            try {
                putIntLE(buffer, position, value);
                position += Integer.BYTES;
            }catch (IndexOutOfBoundsException _) {
                throw ProtobufSerializationException.underflow();
            }
        }

        @Override
        public void writeRawFixedInt64(long value) {
            try {
                putLongLE(buffer, position, value);
                position += Long.BYTES;
            }catch (IndexOutOfBoundsException _) {
                throw ProtobufSerializationException.underflow();
            }
        }

        @Override
        public void writeRawFloat(float entry) {
            try {
                putFloatLE(buffer, position, entry);
                position += Float.BYTES;
            }catch (IndexOutOfBoundsException _) {
                throw ProtobufSerializationException.underflow();
            }
        }

        @Override
        public void writeRawDouble(double entry) {
            try {
                putDoubleLE(buffer, position, entry);
                position += Double.BYTES;
            }catch (IndexOutOfBoundsException _) {
                throw ProtobufSerializationException.underflow();
            }
        }

        @Override
        public void writeRawVarInt32(int entry) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeRawVarInt64(long entry) {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] toOutput() {
            if (buffer.length != position) {
                throw ProtobufSerializationException.mismatch();
            }else {
                return buffer;
            }
        }

        @Override
        public DataType rawDataTypePreference() {
            return DataType.BYTE_ARRAY;
        }

        @Override
        public void close() {

        }
    }

    private static final class ByteBufferWriter extends ProtobufWriter<ByteBuffer> {
        private final ByteBuffer buffer;

        private ByteBufferWriter(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        @Override
        public void writeRawByte(byte entry) {
            try {
                buffer.put(entry);
            }catch (BufferOverflowException _) {
                throw ProtobufSerializationException.underflow();
            }
        }

        @Override
        public void writeRawBytes(byte[] entry, int offset, int length) {
            try {
                buffer.put(entry, offset, length);
            }catch (BufferOverflowException _) {
                throw ProtobufSerializationException.underflow();
            }
        }

        @Override
        public void writeRawBuffer(ByteBuffer entry) {
            try {
                buffer.put(entry.duplicate());
            } catch (BufferOverflowException _) {
                throw ProtobufSerializationException.underflow();
            }
        }

        @Override
        public void writeRawMemorySegment(MemorySegment entry) {
            try {
                buffer.put(entry.asByteBuffer());
            } catch (BufferOverflowException _) {
                throw ProtobufSerializationException.underflow();
            }
        }

        @Override
        public void writeRawFixedInt32(int value) {
            try {
                var position = buffer.position();
                putIntLE(buffer, position, value);
                buffer.position(position + Integer.BYTES);
            } catch (IndexOutOfBoundsException _) {
                throw ProtobufSerializationException.underflow();
            }
        }

        @Override
        public void writeRawFixedInt64(long value) {
            try {
                var position = buffer.position();
                putLongLE(buffer, position, value);
                buffer.position(position + Long.BYTES);
            } catch (IndexOutOfBoundsException _) {
                throw ProtobufSerializationException.underflow();
            }
        }

        @Override
        public void writeRawFloat(float entry) {
            try {
                var position = buffer.position();
                putFloatLE(buffer, position, entry);
                buffer.position(position + Float.BYTES);
            } catch (IndexOutOfBoundsException _) {
                throw ProtobufSerializationException.underflow();
            }
        }

        @Override
        public void writeRawDouble(double entry) {
            try {
                var position = buffer.position();
                putDoubleLE(buffer, position, entry);
                buffer.position(position + Double.BYTES);
            } catch (IndexOutOfBoundsException _) {
                throw ProtobufSerializationException.underflow();
            }
        }

        @Override
        public void writeRawVarInt32(int entry) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeRawVarInt64(long entry) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByteBuffer toOutput() {
            if (buffer.hasRemaining()) {
                throw ProtobufSerializationException.mismatch();
            } else {
                return buffer;
            }
        }

        @Override
        public DataType rawDataTypePreference() {
            return DataType.BYTE_BUFFER;
        }

        @Override
        public void close() {

        }
    }

    private static final class MemorySegmentWriter extends ProtobufWriter<MemorySegment> {
        private final MemorySegment memorySegment;
        private long position;

        private MemorySegmentWriter(MemorySegment memorySegment) {
            this.memorySegment = memorySegment;
        }

        @Override
        public void writeRawByte(byte entry) {
            try {
                memorySegment.set(ValueLayout.JAVA_BYTE, position, entry);
                position++;
            } catch (IndexOutOfBoundsException _) {
                throw ProtobufSerializationException.underflow();
            }
        }

        @Override
        public void writeRawBytes(byte[] entry, int offset, int length) {
            try {
                MemorySegment.copy(
                        entry,
                        offset,
                        memorySegment,
                        ValueLayout.JAVA_BYTE,
                        position,
                        length
                );
                position += length;
            } catch (IndexOutOfBoundsException _) {
                throw ProtobufSerializationException.underflow();
            }
        }

        @Override
        public void writeRawBuffer(ByteBuffer entry) {
            writeRawMemorySegment(MemorySegment.ofBuffer(entry));
        }

        @Override
        public void writeRawMemorySegment(MemorySegment entry) {
            try {
                var length = entry.byteSize();
                MemorySegment.copy(
                        entry,
                        0,
                        memorySegment,
                        position,
                        length
                );
                position += length;
            } catch (IndexOutOfBoundsException _) {
                throw ProtobufSerializationException.underflow();
            }
        }

        @Override
        public void writeRawFixedInt32(int entry) {
            try {
                memorySegment.set(ValueLayout.JAVA_INT_UNALIGNED, position, entry);
                position += Integer.BYTES;
            } catch (IndexOutOfBoundsException _) {
                throw ProtobufSerializationException.underflow();
            }
        }

        @Override
        public void writeRawFixedInt64(long entry) {
            try {
                memorySegment.set(ValueLayout.JAVA_LONG_UNALIGNED, position, entry);
                position += Long.BYTES;
            } catch (IndexOutOfBoundsException _) {
                throw ProtobufSerializationException.underflow();
            }
        }

        @Override
        public void writeRawFloat(float entry) {
            try {
                memorySegment.set(ValueLayout.JAVA_FLOAT_UNALIGNED, position, entry);
                position += Float.BYTES;
            } catch (IndexOutOfBoundsException _) {
                throw ProtobufSerializationException.underflow();
            }
        }

        @Override
        public void writeRawDouble(double entry) {
            try {
                memorySegment.set(ValueLayout.JAVA_DOUBLE_UNALIGNED, position, entry);
                position += Double.BYTES;
            } catch (IndexOutOfBoundsException _) {
                throw ProtobufSerializationException.underflow();
            }
        }

        @Override
        public void writeRawVarInt32(int entry) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeRawVarInt64(long entry) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MemorySegment toOutput() {
            return memorySegment;
        }

        @Override
        public DataType rawDataTypePreference() {
            return DataType.MEMORY_SEGMENT;
        }

        @Override
        public void close() {

        }
    }

    private static final class InputStreamWriter extends ProtobufWriter<OutputStream> {
        private static final int BUFFER_LENGTH = 8192;

        private final OutputStream outputStream;
        private final byte[] buffer;

        InputStreamWriter(OutputStream outputStream) {
            this.outputStream = outputStream;
            this.buffer = new byte[BUFFER_LENGTH];
        }


        @Override
        public void writeRawByte(byte entry) {
            try {
                outputStream.write(entry);
            }catch (IOException e) {
                throw new ProtobufSerializationException("Cannot write to output stream", e);
            }
        }

        @Override
        public void writeRawBytes(byte[] entry, int offset, int length) {
            try {
                outputStream.write(entry, offset, length);
            }catch (IOException e) {
                throw new ProtobufSerializationException("Cannot write to output stream", e);
            }
        }

        @Override
        public void writeRawBuffer(ByteBuffer entry) {
            try {
                if(entry.hasArray()) {
                    outputStream.write(entry.array(), entry.arrayOffset() + entry.position(), entry.remaining());
                }else {
                    while (entry.hasRemaining()) {
                        var readable = Math.min(entry.remaining(), buffer.length);
                        entry.put(buffer, entry.position(), readable);
                        outputStream.write(buffer, 0, readable);
                    }
                }
            }catch (IOException e) {
                throw new ProtobufSerializationException("Cannot write to output stream", e);
            }
        }

        @Override
        public void writeRawMemorySegment(MemorySegment entry) {
            try {
                var offset = 0L;
                var limit = entry.byteSize();
                while (offset < limit) {
                    var readable = (int) Math.min(limit - offset, buffer.length);
                    MemorySegment.copy(
                            entry,
                            ValueLayout.JAVA_BYTE,
                            offset,
                            buffer,
                            0,
                            readable
                    );
                    outputStream.write(buffer, 0, readable);
                    offset += readable;
                }
            }catch (IOException e) {
                throw new ProtobufSerializationException("Cannot write to output stream", e);
            }
        }

        @Override
        public void writeRawFixedInt32(int entry) {
            try {
                putIntLE(buffer, 0, entry);
                outputStream.write(buffer, 0, Integer.BYTES);
            }catch (IOException e) {
                throw new ProtobufSerializationException("Cannot write to output stream", e);
            }
        }

        @Override
        public void writeRawFixedInt64(long entry) {
            try {
                putLongLE(buffer, 0, entry);
                outputStream.write(buffer, 0, Long.BYTES);
            }catch (IOException e) {
                throw new ProtobufSerializationException("Cannot write to output stream", e);
            }
        }

        @Override
        public void writeRawFloat(float entry) {
            try {
                putFloatLE(buffer, 0, entry);
                outputStream.write(buffer, 0, Float.BYTES);
            }catch (IOException e) {
                throw new ProtobufSerializationException("Cannot write to output stream", e);
            }
        }

        @Override
        public void writeRawDouble(double entry) {
            try {
                putDoubleLE(buffer, 0, entry);
                outputStream.write(buffer, 0, Double.BYTES);
            }catch (IOException e) {
                throw new ProtobufSerializationException("Cannot write to output stream", e);
            }
        }

        @Override
        public void writeRawVarInt32(int entry) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeRawVarInt64(long entry) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OutputStream toOutput() {
            return outputStream;
        }

        @Override
        public DataType rawDataTypePreference() {
            return DataType.BYTE_ARRAY;
        }

        @Override
        public void close() throws IOException {
            outputStream.close();
        }
    }
}
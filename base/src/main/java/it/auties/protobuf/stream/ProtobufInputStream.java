// Protocol Buffers - Google's data interchange format
// Copyright 2008 Google Inc.  All rights reserved.
//
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file or at
// https://developers.google.com/open-source/licenses/bsd

// I'm not sure if the LICENSE copyright header is necessary as only two methods in this class are taken from Google's source code
// But just to be sure I included it

package it.auties.protobuf.stream;

import it.auties.protobuf.exception.ProtobufDeserializationException;
import it.auties.protobuf.model.ProtobufUnknownValue;
import it.auties.protobuf.model.ProtobufWireType;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * An abstract input stream for reading Protocol Buffer encoded data.
 * <p>
 * This class provides a comprehensive API for deserializing Protocol Buffer messages from various
 * data sources including byte arrays, ByteBuffers, and InputStreams. It supports all Protocol Buffer
 * wire types and provides both type-safe and unchecked reading methods.
 * <p>
 *
 * @see ProtobufOutputStream
 * @see AutoCloseable
 */
@SuppressWarnings("unused")
public abstract class ProtobufInputStream implements AutoCloseable {
    private static final ValueLayout.OfInt FIXED_INT32_LAYOUT = ValueLayout.JAVA_INT_UNALIGNED
            .withOrder(ByteOrder.LITTLE_ENDIAN);
    private static final ValueLayout.OfLong FIXED_INT64_LAYOUT = ValueLayout.JAVA_LONG_UNALIGNED
            .withOrder(ByteOrder.LITTLE_ENDIAN);
    private static final ValueLayout.OfFloat FLOAT_LAYOUT = ValueLayout.JAVA_FLOAT_UNALIGNED
            .withOrder(ByteOrder.LITTLE_ENDIAN);
    private static final ValueLayout.OfDouble DOUBLE_LAYOUT = ValueLayout.JAVA_DOUBLE_UNALIGNED
            .withOrder(ByteOrder.LITTLE_ENDIAN);

    protected int wireType;
    protected long index;
    protected ProtobufInputStream() {
        resetPropertyTag();
    }

    public static ProtobufInputStream fromBytes(byte[] bytes) {
        return new ByteArraySource(bytes, 0, bytes.length);
    }

    public static ProtobufInputStream fromBytes(byte[] bytes, int offset, int length) {
        return new ByteArraySource(bytes, offset, offset + length);
    }

    public static ProtobufInputStream fromBuffer(ByteBuffer buffer) {
        return new ByteBufferSource(buffer);
    }

    public static ProtobufInputStream fromStream(InputStream buffer) {
        return new InputStreamSource(buffer, true);
    }

    public static ProtobufInputStream fromStream(InputStream buffer, boolean autoclose) {
        return new InputStreamSource(buffer, autoclose);
    }

    public static ProtobufInputStream fromMemorySegment(MemorySegment segment) {
        return new MemorySegmentSource(segment);
    }

    public int propertyWireType() {
        return wireType;
    }

    public long propertyIndex() {
        return index;
    }

    public boolean readPropertyTag() {
        if(wireType != -1 || index != -1) {
            throw ProtobufDeserializationException.invalidPropertyState("a property tag was already read");
        } else if(isFinished()) {
            return false;
        }else {
            var rawTag = readRawVarInt32();
            this.wireType = rawTag & 7;
            this.index = rawTag >>> 3;
            if(index == 0) {
                throw ProtobufDeserializationException.invalidFieldIndex(index);
            }
            return wireType != ProtobufWireType.WIRE_TYPE_END_OBJECT;
        }
    }

    public void resetPropertyTag() {
        if(wireType == -1 || index == -1) {
            throw ProtobufDeserializationException.invalidPropertyState("no property tag");
        } else {
            this.wireType = -1;
            this.index = -1;
        }
    }

    public int readLengthDelimitedPropertyLength() {
        var length = readRawVarInt32();
        if(length < 0) {
            throw ProtobufDeserializationException.negativeLength(length);
        } else {
            return length;
        }
    }

    public ProtobufInputStream readLengthDelimitedProperty() {
        if(wireType != ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        } else {
            var size = readLengthDelimitedPropertyLength();
            var result = readRawLengthDelimited(size);
            resetPropertyTag();
            return result;
        }
    }

    public void readStartGroupProperty(long groupIndex) {
        if((wireType == -1 && !readPropertyTag()) || wireType != ProtobufWireType.WIRE_TYPE_START_OBJECT || index != groupIndex) {
            throw ProtobufDeserializationException.invalidStartObject(groupIndex);
        } else {
            resetPropertyTag();
        }
    }

    public void readEndGroupProperty(long groupIndex) {
        if(wireType != ProtobufWireType.WIRE_TYPE_END_OBJECT) {
            throw ProtobufDeserializationException.malformedGroup();
        } else if(index != groupIndex) {
            throw ProtobufDeserializationException.invalidEndObject(index, groupIndex);
        } else {
            resetPropertyTag();
        }
    }

    public float readFloatProperty() {
        if(wireType != ProtobufWireType.WIRE_TYPE_FIXED32) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        } else {
            var result = Float.intBitsToFloat(readRawFixedInt32());
            resetPropertyTag();
            return result;
        }
    }

    public double readDoubleProperty() {
        if(wireType != ProtobufWireType.WIRE_TYPE_FIXED64) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        } else {
            var result = Double.longBitsToDouble(readRawFixedInt64());
            resetPropertyTag();
            return result;
        }
    }

    public boolean readBoolProperty() {
        if(wireType != ProtobufWireType.WIRE_TYPE_VAR_INT) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        } else {
            var result = readRawVarInt64() == 1;
            resetPropertyTag();
            return result;
        }
    }

    public int readInt32Property() {
        return readVarInt32();
    }

    public int readUInt32Property() {
        return readVarInt32();
    }

    private int readVarInt32() {
        if(wireType != ProtobufWireType.WIRE_TYPE_VAR_INT) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        } else {
            var result = readRawVarInt32();
            resetPropertyTag();
            return result;
        }
    }

    public int readSInt32Property() {
        if(wireType != ProtobufWireType.WIRE_TYPE_VAR_INT) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        } else {
            return readRawZigZagVarInt32();
        }
    }

    public long readInt64Property() {
        return readVarInt64();
    }

    public long readUInt64Property() {
        return readVarInt64();
    }

    private long readVarInt64() {
        if(wireType != ProtobufWireType.WIRE_TYPE_VAR_INT) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        } else {
            var result = readRawVarInt64();
            resetPropertyTag();
            return result;
        }
    }

    public long readSInt64Property() {
        if(wireType != ProtobufWireType.WIRE_TYPE_VAR_INT) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        } else {
            var result = readRawZigZagVarInt64();
            resetPropertyTag();
            return result;
        }
    }

    public int readFixed32Property() {
        if(wireType != ProtobufWireType.WIRE_TYPE_FIXED32) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        } else {
            var result = readRawFixedInt32();
            resetPropertyTag();
            return result;
        }
    }

    public long readFixed64Property() {
        if(wireType != ProtobufWireType.WIRE_TYPE_FIXED64) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        } else {
            var result = readRawFixedInt64();
            resetPropertyTag();
            return result;
        }
    }
    
    public ProtobufUnknownValue readUnknownProperty() {
        return switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_FIXED32 -> {
                var value = readRawFixedInt32();
                yield new ProtobufUnknownValue.Fixed32(value);
            }
            case ProtobufWireType.WIRE_TYPE_FIXED64 -> {
                var value = readRawFixedInt64();
                yield new ProtobufUnknownValue.Fixed64(value);
            }
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> {
                var size = readLengthDelimitedPropertyLength();
                yield switch (preferredRawDataType()) {
                    case BYTE_ARRAY -> new ProtobufUnknownValue.LengthDelimited.AsByteArray(readRawBytes(size));
                    case BYTE_BUFFER -> new ProtobufUnknownValue.LengthDelimited.AsByteBuffer(readRawBuffer(size));
                    case MEMORY_SEGMENT -> new ProtobufUnknownValue.LengthDelimited.AsMemorySegment(readRawMemorySegment(size));
                };
            }
            case ProtobufWireType.WIRE_TYPE_START_OBJECT -> {
                var result = new HashMap<Long, ProtobufUnknownValue>();
                var index = this.index;
                while (readPropertyTag()) {
                    var key = this.index;
                    var value = readUnknownProperty(); // TODO: Maybe no recursion?
                    result.put(key, value);
                }
                readEndGroupProperty(index);
                yield new ProtobufUnknownValue.Group(result);
            }
            case ProtobufWireType.WIRE_TYPE_VAR_INT -> {
                var value = readRawVarInt64();
                yield new ProtobufUnknownValue.VarInt(value);
            }
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
    }

    public float[] readPackedFloatProperty() {
        var result = switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> readRawPackedFloat();
            case ProtobufWireType.WIRE_TYPE_FIXED32 -> new float[]{Float.intBitsToFloat(readRawFixedInt32())};
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
        resetPropertyTag();
        return result;
    }

    public double[] readPackedDoubleProperty() {
        var result = switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> readRawPackedDouble();
            case ProtobufWireType.WIRE_TYPE_FIXED64 -> new double[]{Double.longBitsToDouble(readRawFixedInt64())};
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
        resetPropertyTag();
        return result;
    }
    
    public int[] readPackedInt32Property() {
        return readPackedVarInt32();
    }

    public int[] readPackedUInt32Property() {
        return readPackedVarInt32();
    }

    private int[] readPackedVarInt32() {
        var result = switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> readRawPackedVarInt32();
            case ProtobufWireType.WIRE_TYPE_VAR_INT -> new int[]{readRawVarInt32()};
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
        resetPropertyTag();
        return result;
    }

    public int[] readPackedSInt32Property() {
        var result = switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> readRawPackedZigZagVarInt32();
            case ProtobufWireType.WIRE_TYPE_VAR_INT -> new int[]{readRawZigZagVarInt32()};
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
        resetPropertyTag();
        return result;
    }

    public long[] readPackedInt64Property() {
        return readPackedVarInt64();
    }

    public long[] readPackedUInt64Property() {
        return readPackedVarInt64();
    }

    private long[] readPackedVarInt64() {
        var result = switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> readRawPackedVarInt64();
            case ProtobufWireType.WIRE_TYPE_VAR_INT -> new long[]{readRawVarInt64()};
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
        resetPropertyTag();
        return result;
    }

    public long[] readPackedSInt64Property() {
        var result = switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> readRawPackedZigZagVarInt64();
            case ProtobufWireType.WIRE_TYPE_VAR_INT -> new long[]{readRawZigZagVarInt64()};
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
        resetPropertyTag();
        return result;
    }

    public boolean[] readPackedBoolProperty() {
        var result = switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> readRawPackedBool();
            case ProtobufWireType.WIRE_TYPE_VAR_INT -> new boolean[]{readBoolProperty()};
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
        resetPropertyTag();
        return result;
    }

    public int[] readPackedFixed32Property() {
        var result = switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> readRawPackedFixedInt32();
            case ProtobufWireType.WIRE_TYPE_FIXED32 -> new int[]{readRawFixedInt32()};
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
        resetPropertyTag();
        return result;
    }

    public long[] readPackedFixed64Property() {
        var result = switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> readRawPackedFixedInt64();
            case ProtobufWireType.WIRE_TYPE_FIXED64 -> new long[]{readRawFixedInt64()};
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
        resetPropertyTag();
        return result;
    }

    public void skipUnknownProperty() {
        switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_VAR_INT -> {
                skipRawVarInt();
                resetPropertyTag();
            }
            case ProtobufWireType.WIRE_TYPE_FIXED32 -> {
                skipRawBytes(Integer.BYTES);
                resetPropertyTag();
            }
            case ProtobufWireType.WIRE_TYPE_FIXED64 -> {
                skipRawBytes(Long.BYTES);
                resetPropertyTag();
            }
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> {
                skipRawBytes(readLengthDelimitedPropertyLength());
                resetPropertyTag();
            }
            case ProtobufWireType.WIRE_TYPE_START_OBJECT -> {
                var index = this.index;
                while (readPropertyTag()) {
                    skipUnknownProperty(); // TODO: Maybe no recursion?
                }
                readEndGroupProperty(index);
            }
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
    }

    public void skipRawVarInt() {
        for (var shift = 0; shift < 64; shift += 7) {
            if ((readRawByte() & 0x80) == 0) {
                return;
            }
        }

        throw ProtobufDeserializationException.malformedVarInt();
    }


    public abstract void skipRawBytes(int size);
    public abstract byte readRawByte();

    public abstract byte[] readRawBytes(int size);
    public abstract ByteBuffer readRawBuffer(int size);
    public abstract MemorySegment readRawMemorySegment(int size);
    public abstract DataType preferredRawDataType();

    public abstract int readRawFixedInt32();
    public abstract long readRawFixedInt64();
    public abstract int readRawVarInt32();
    public abstract long readRawVarInt64();
    public abstract int readRawZigZagVarInt32();
    public abstract long readRawZigZagVarInt64();

    public abstract float[] readRawPackedFloat();
    public abstract double[] readRawPackedDouble();
    public abstract int[] readRawPackedVarInt32();
    public abstract int[] readRawPackedZigZagVarInt32();
    public abstract long[] readRawPackedVarInt64();
    public abstract long[] readRawPackedZigZagVarInt64();
    public abstract boolean[] readRawPackedBool();
    public abstract int[] readRawPackedFixedInt32();
    public abstract long[] readRawPackedFixedInt64();

    public abstract ProtobufInputStream readRawLengthDelimited(int size);

    public abstract boolean isFinished();

    public enum DataType {
        BYTE_ARRAY,
        BYTE_BUFFER,
        MEMORY_SEGMENT
    }

    private static final class InputStreamSource extends ProtobufInputStream {
        private static final int MAX_VAR_INT_SIZE = 10;

        private final InputStream inputStream;
        private final boolean autoclose;
        private final long length;
        private long position;
        
        private byte nextBufferedValue;
        private boolean hasNextBufferedValue;
        
        private final byte[] scalarBuffer;

        private InputStreamSource(InputStream inputStream, boolean autoclose) {
            Objects.requireNonNull(inputStream, "inputStream cannot be null");
            this.inputStream = inputStream;
            this.autoclose = autoclose;
            this.length = -1;
            this.scalarBuffer = new byte[Long.BYTES];
        }

        private InputStreamSource(InputStream inputStream, long length, byte[] scalarBuffer) {
            this.inputStream = inputStream;
            this.autoclose = false;
            this.length = length;
            this.scalarBuffer = scalarBuffer;
        }

        @Override
        public byte readRawByte() {
            position++;
            if(hasNextBufferedValue) {
                hasNextBufferedValue = false;
                return nextBufferedValue;
            } else {
                try {
                    var read = inputStream.read();
                    if(read == -1) {
                        throw ProtobufDeserializationException.truncatedMessage();
                    } else {
                        return (byte) read;
                    }
                }catch (IOException exception) {
                    throw ProtobufDeserializationException.truncatedMessage(exception);
                }
            }
        }

        @Override
        public byte[] readRawBytes(int size) {
            try {
                var result = new byte[size];
                readRawBytes(result, size);
                return result;
            }catch (NegativeArraySizeException _) {
                throw new IllegalArgumentException("size cannot be negative");
            }
        }
        
        private void readRawBytes(byte[] output, int length) {
            position += length;
            var offset = 0;
            if(length > 0 && hasNextBufferedValue) {
                hasNextBufferedValue = false;
                offset++;
                output[0] = nextBufferedValue;
            }
            while (offset < length) {
                try {
                    var read = inputStream.read(output, offset, length - offset);
                    if (read == -1) {
                        throw ProtobufDeserializationException.truncatedMessage();
                    } else {
                        offset += read;
                    }
                }catch (IOException exception) {
                    throw ProtobufDeserializationException.truncatedMessage(exception);
                }
            }
        }

        @Override
        public ByteBuffer readRawBuffer(int size) {
            var bytes = readRawBytes(size);
            return ByteBuffer.wrap(bytes);
        }

        @Override
        public MemorySegment readRawMemorySegment(int size) {
            var bytes = readRawBytes(size);
            return MemorySegment.ofArray(bytes);
        }

        @Override
        public DataType preferredRawDataType() {
            return DataType.BYTE_ARRAY;
        }

        @Override
        public boolean isFinished() {
            if(length != -1) {
                return position >= length;
            } else if(hasNextBufferedValue){
                return false;
            } else {
                try {
                    position++;
                    var read = inputStream.read();
                    if(read == -1) {
                        return true;
                    } else {
                        position--;
                        hasNextBufferedValue = true;
                        nextBufferedValue = (byte) read;
                        return false;
                    }
                }catch (IOException exception) {
                    throw ProtobufDeserializationException.truncatedMessage(exception);
                }
            }
        }

        @Override
        public void skipRawBytes(int length) {
            if(length < 0) {
                throw new IllegalArgumentException("length cannot be negative");
            }

            position += length;

            var offset = 0L;
            if(length > 0 && hasNextBufferedValue) {
                hasNextBufferedValue = false;
                offset++;
            }
            while (offset < length) {
                try {
                    var skipped = inputStream.skip(length - offset);
                    if(skipped == 0) {
                        if(isFinished()) {
                            throw ProtobufDeserializationException.truncatedMessage();
                        }
                    } else {
                        offset += skipped;   
                    }
                }catch (IOException exception) {
                    throw ProtobufDeserializationException.truncatedMessage(exception);
                }
            }
        }

        @Override
        public int readRawFixedInt32() {
            readRawBytes(scalarBuffer, Integer.BYTES);
            return (scalarBuffer[0] & 0xFF)
                   | (scalarBuffer[1] & 0xFF) << 8
                   | (scalarBuffer[2] & 0xFF) << 16
                   | (scalarBuffer[3] & 0xFF) << 24;
        }

        @Override
        public long readRawFixedInt64() {
            readRawBytes(scalarBuffer, Long.BYTES);
            return (scalarBuffer[0] & 0xFFL)
                    | (scalarBuffer[1] & 0xFFL) << 8
                    | (scalarBuffer[2] & 0xFFL) << 16
                    | (scalarBuffer[3] & 0xFFL) << 24
                    | (scalarBuffer[4] & 0xFFL) << 32
                    | (scalarBuffer[5] & 0xFFL) << 40
                    | (scalarBuffer[6] & 0xFFL) << 48
                    | (scalarBuffer[7] & 0xFFL) << 56;
        }

        @Override
        public InputStreamSource readRawLengthDelimited(int size) {
            var result = new InputStreamSource(inputStream, size, scalarBuffer);
            position += size;
            return result;
        }

        @Override
        public void close() throws IOException {
            if(autoclose) {
                inputStream.close();
            }
        }

        private int readFromStream(byte[] result, int offset, int length) {
            try {
                return inputStream.read(result, offset, length);
            }catch (IOException exception) {
                throw ProtobufDeserializationException.truncatedMessage(exception);
            }
        }

        private long skipFromStream(long length) {
            try {
                return inputStream.skip(length);
            }catch (IOException exception) {
                throw ProtobufDeserializationException.truncatedMessage(exception);
            }
        }

        @Override
        public int readRawVarInt32() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long readRawVarInt64() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int readRawZigZagVarInt32() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long readRawZigZagVarInt64() {
            throw new UnsupportedOperationException();
        }

        @Override
        public float[] readRawPackedFloat() {
            var length = readRawVarInt32();
            var bytes = readRawBytes(length);
            return MemorySegment.ofArray(bytes)
                    .toArray(FLOAT_LAYOUT);
        }

        @Override
        public double[] readRawPackedDouble() {
            var length = readRawVarInt32();
            var bytes = readRawBytes(length);
            return MemorySegment.ofArray(bytes)
                    .toArray(DOUBLE_LAYOUT);
        }

        @Override
        public int[] readRawPackedVarInt32() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int[] readRawPackedZigZagVarInt32() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long[] readRawPackedVarInt64() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long[] readRawPackedZigZagVarInt64() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean[] readRawPackedBool() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int[] readRawPackedFixedInt32() {
            var length = readRawVarInt32();
            var bytes = readRawBytes(length);
            return MemorySegment.ofArray(bytes)
                    .toArray(FIXED_INT32_LAYOUT);
        }

        @Override
        public long[] readRawPackedFixedInt64() {
            var length = readRawVarInt32();
            var bytes = readRawBytes(length);
            return MemorySegment.ofArray(bytes)
                    .toArray(FIXED_INT64_LAYOUT);
        }
    }

    private static final class ByteArraySource extends ProtobufInputStream {
        private final byte[] buffer;
        private final int limit;
        private int offset;

        private ByteArraySource(byte[] buffer, int offset, int limit) {
            Objects.requireNonNull(buffer, "buffer cannot be null");
            Objects.checkFromToIndex(offset, limit, buffer.length);
            this.buffer = buffer;
            this.offset = offset;
            this.limit = limit;
        }

        @Override
        public byte readRawByte() {
            if(offset >= limit) {
                throw ProtobufDeserializationException.truncatedMessage();
            }else {
                return buffer[offset++];
            }
        }

        @Override
        public byte[] readRawBytes(int size) {
            try {
                var result = new byte[size];
                System.arraycopy(buffer, offset, result, 0, size);
                offset += size;
                return result;
            } catch (NegativeArraySizeException _) {
                throw ProtobufDeserializationException.negativeLength(size);
            }catch (IndexOutOfBoundsException error) {
                throw ProtobufDeserializationException.truncatedMessage();
            }
        }

        @Override
        public ByteBuffer readRawBuffer(int size) {
            try {
                var result = ByteBuffer.wrap(buffer, offset, size);
                offset += size;
                return result;
            }catch (IndexOutOfBoundsException error) {
                if(size < 0) {
                    throw ProtobufDeserializationException.negativeLength(size);
                } else {
                    throw ProtobufDeserializationException.truncatedMessage();
                }
            }
        }

        @Override
        public MemorySegment readRawMemorySegment(int size) {
            try {
                var result = MemorySegment.ofBuffer(ByteBuffer.wrap(buffer, offset, offset + size));
                offset += size;
                return result;
            }catch (IndexOutOfBoundsException error) {
                if(size < 0) {
                    throw ProtobufDeserializationException.negativeLength(size);
                } else {
                    throw ProtobufDeserializationException.truncatedMessage();
                }
            }
        }

        @Override
        public DataType preferredRawDataType() {
            return DataType.BYTE_ARRAY;
        }

        @Override
        public boolean isFinished() {
            return offset >= limit;
        }

        @Override
        public void skipRawBytes(int size) {
            if(size < 0) {
                throw new IllegalArgumentException("size cannot be negative");
            } else {
                offset += size;
                if(offset > limit) {
                    throw ProtobufDeserializationException.truncatedMessage();
                }
            }
        }

        @Override
        public int readRawFixedInt32() {
            if(offset + Integer.BYTES > limit) {
                throw ProtobufDeserializationException.truncatedMessage();
            } else {
                return (buffer[offset++] & 0xFF)
                       | (buffer[offset++] & 0xFF) << 8
                       | (buffer[offset++] & 0xFF) << 16
                       | (buffer[offset++] & 0xFF) << 24;
            }
        }

        @Override
        public long readRawFixedInt64() {
            if(offset + Long.BYTES > limit) {
                throw ProtobufDeserializationException.truncatedMessage();
            } else {
                return (buffer[offset++] & 0xFFL)
                        | (buffer[offset++] & 0xFFL) << 8
                        | (buffer[offset++] & 0xFFL) << 16
                        | (buffer[offset++] & 0xFFL) << 24
                        | (buffer[offset++] & 0xFFL) << 32
                        | (buffer[offset++] & 0xFFL) << 40
                        | (buffer[offset++] & 0xFFL) << 48
                        | (buffer[offset++] & 0xFFL) << 56;
            }
        }

        @Override
        public ByteArraySource readRawLengthDelimited(int size) {
            try {
                var result = new ByteArraySource(buffer, offset, offset + size);
                offset += size;
                return result;
            } catch (IndexOutOfBoundsException _) {
                throw ProtobufDeserializationException.truncatedMessage();
            }
        }

        @Override
        public void close() {

        }

        @Override
        public int readRawVarInt32() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long readRawVarInt64() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int readRawZigZagVarInt32() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long readRawZigZagVarInt64() {
            throw new UnsupportedOperationException();
        }

        @Override
        public float[] readRawPackedFloat() {
            var length = readRawVarInt32();
            var buffer = ByteBuffer.wrap(this.buffer, offset, offset + length)
                    .asFloatBuffer();
            var result = new float[buffer.limit()];
            buffer.get(result);
            return result;
        }

        @Override
        public double[] readRawPackedDouble() {
            var length = readRawVarInt32();
            var buffer = ByteBuffer.wrap(this.buffer, offset, offset + length)
                    .asDoubleBuffer();
            var result = new double[buffer.limit()];
            buffer.get(result);
            return result;
        }

        @Override
        public int[] readRawPackedVarInt32() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int[] readRawPackedZigZagVarInt32() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long[] readRawPackedVarInt64() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long[] readRawPackedZigZagVarInt64() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean[] readRawPackedBool() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int[] readRawPackedFixedInt32() {
            var length = readRawVarInt32();
            var buffer = ByteBuffer.wrap(this.buffer, offset, offset + length)
                    .asIntBuffer();
            var result = new int[buffer.limit()];
            buffer.get(result);
            return result;
        }

        @Override
        public long[] readRawPackedFixedInt64() {
            var length = readRawVarInt32();
            var buffer = ByteBuffer.wrap(this.buffer, offset, offset + length)
                    .asLongBuffer();
            var result = new long[buffer.limit()];
            buffer.get(result);
            return result;
        }
    }

    private static final class ByteBufferSource extends ProtobufInputStream {
        private static final ThreadLocal<CharsetDecoder> UTF8_DECODER = ThreadLocal.withInitial(() ->
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPLACE)
                        .onUnmappableCharacter(CodingErrorAction.REPLACE));

        private final ByteBuffer buffer;
        private ByteBufferSource(ByteBuffer buffer) {
            Objects.requireNonNull(buffer, "buffer cannot be null");
            this.buffer = buffer.duplicate()
                    .order(ByteOrder.LITTLE_ENDIAN);
        }

        @Override
        public byte readRawByte() {
            try {
                return buffer.get();
            }catch (BufferUnderflowException _) {
                throw ProtobufDeserializationException.truncatedMessage();
            }
        }

        @Override
        public byte[] readRawBytes(int size) {
            try {
                var result = new byte[size];
                buffer.get(result);
                return result;
            } catch (BufferUnderflowException _) {
                throw ProtobufDeserializationException.truncatedMessage();
            } catch (NegativeArraySizeException _) {
                throw new IllegalArgumentException("size cannot be negative");
            }
        }

        @Override
        public ByteBuffer readRawBuffer(int size) {
            try {
                var position = buffer.position();
                var result = buffer.slice(position, size);
                buffer.position(position + size);
                return result;
            }catch (IndexOutOfBoundsException _) {
                throw ProtobufDeserializationException.truncatedMessage();
            }
        }

        @Override
        public MemorySegment readRawMemorySegment(int size) {
            try {
                var position = buffer.position();
                var result = buffer.slice(position, size);
                buffer.position(position + size);
                return MemorySegment.ofBuffer(result);
            }catch (IndexOutOfBoundsException _) {
                throw ProtobufDeserializationException.truncatedMessage();
            }
        }

        @Override
        public DataType preferredRawDataType() {
            return DataType.BYTE_BUFFER;
        }

        @Override
        public boolean isFinished() {
            return !buffer.hasRemaining();
        }

        @Override
        public void skipRawBytes(int size) {
            try {
                buffer.position(buffer.position() + size);
            } catch (IllegalArgumentException _) {
                throw ProtobufDeserializationException.truncatedMessage();
            }
        }

        @Override
        public int readRawFixedInt32() {
            try {
                return buffer.getInt();
            }catch (BufferUnderflowException _) {
                throw ProtobufDeserializationException.truncatedMessage();
            }
        }

        @Override
        public long readRawFixedInt64() {
           try {
               return buffer.getLong();
           }catch (BufferUnderflowException _) {
               throw ProtobufDeserializationException.truncatedMessage();
           }
        }

        @Override
        public ByteBufferSource readRawLengthDelimited(int size) {
            try {
                var position = buffer.position();
                var result = new ByteBufferSource(buffer.slice(position, size));
                buffer.position(position + size);
                return result;
            } catch (IllegalArgumentException _) {
                throw ProtobufDeserializationException.truncatedMessage();
            }
        }

        @Override
        public void close() {
            
        }

        @Override
        public int readRawVarInt32() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long readRawVarInt64() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int readRawZigZagVarInt32() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long readRawZigZagVarInt64() {
            throw new UnsupportedOperationException();
        }

        @Override
        public float[] readRawPackedFloat() {
            var length = readRawVarInt32();
            var segment = readRawMemorySegment(length);
            return segment.toArray(FLOAT_LAYOUT);
        }

        @Override
        public double[] readRawPackedDouble() {
            var length = readRawVarInt32();
            var segment = readRawMemorySegment(length);
            return segment.toArray(DOUBLE_LAYOUT);
        }

        @Override
        public int[] readRawPackedVarInt32() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int[] readRawPackedZigZagVarInt32() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long[] readRawPackedVarInt64() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long[] readRawPackedZigZagVarInt64() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean[] readRawPackedBool() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int[] readRawPackedFixedInt32() {
            var length = readRawVarInt32();
            var segment = readRawMemorySegment(length);
            return segment.toArray(FIXED_INT32_LAYOUT);
        }

        @Override
        public long[] readRawPackedFixedInt64() {
            var length = readRawVarInt32();
            var segment = readRawMemorySegment(length);
            return segment.toArray(FIXED_INT64_LAYOUT);
        }
    }

    private static final class MemorySegmentSource extends ProtobufInputStream {
        private final MemorySegment segment;
        private int position;

        private MemorySegmentSource(MemorySegment segment) {
            Objects.requireNonNull(segment, "segment cannot be null");
            this.segment = segment;
            this.position = 0;
        }

        @Override
        public byte readRawByte() {
            try {
                var result = segment.get(ValueLayout.OfByte.JAVA_BYTE, position);
                position++;
                return result;
            }catch (BufferUnderflowException _) {
                throw ProtobufDeserializationException.truncatedMessage();
            }
        }

        @Override
        public byte[] readRawBytes(int size) {
            try {
                var result = segment.asSlice(position, position + size)
                        .toArray(ValueLayout.OfByte.JAVA_BYTE);
                position += size;
                return result;
            } catch (BufferUnderflowException _) {
                throw ProtobufDeserializationException.truncatedMessage();
            } catch (NegativeArraySizeException _) {
                throw new IllegalArgumentException("size cannot be negative");
            }
        }

        @Override
        public ByteBuffer readRawBuffer(int size) {
            try {
                var result = segment.asSlice(position, position + size);
                position += size;
                return result.asByteBuffer();
            }catch (IndexOutOfBoundsException _) {
                throw ProtobufDeserializationException.truncatedMessage();
            }
        }

        @Override
        public MemorySegment readRawMemorySegment(int size) {
            try {
                var result = segment.asSlice(position, position + size);
                position += size;
                return result;
            }catch (IndexOutOfBoundsException _) {
                throw ProtobufDeserializationException.truncatedMessage();
            }
        }

        @Override
        public DataType preferredRawDataType() {
            return DataType.MEMORY_SEGMENT;
        }

        @Override
        public boolean isFinished() {
            return position >= segment.byteSize();
        }

        @Override
        public void skipRawBytes(int size) {
            position += size;
            if(position > size) {
                throw ProtobufDeserializationException.truncatedMessage();
            }
        }

        @Override
        public int readRawFixedInt32() {
            var result = segment.getAtIndex(ValueLayout.OfInt.JAVA_INT, position);
            position += Integer.BYTES;
            return result;
        }

        @Override
        public long readRawFixedInt64() {
            var result = segment.getAtIndex(ValueLayout.OfLong.JAVA_LONG, position);
            position += Long.BYTES;
            return result;
        }

        @Override
        public MemorySegmentSource readRawLengthDelimited(int size) {
            var result = new MemorySegmentSource(segment.asSlice(position, size));
            position += size;
            return result;
        }

        @Override
        public void close() {

        }

        @Override
        public int readRawVarInt32() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long readRawVarInt64() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int readRawZigZagVarInt32() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long readRawZigZagVarInt64() {
            throw new UnsupportedOperationException();
        }

        @Override
        public float[] readRawPackedFloat() {
            var length = readRawVarInt32();
            var segment = readRawMemorySegment(length);
            return segment.toArray(FLOAT_LAYOUT);
        }

        @Override
        public double[] readRawPackedDouble() {
            var length = readRawVarInt32();
            var segment = readRawMemorySegment(length);
            return segment.toArray(DOUBLE_LAYOUT);
        }

        @Override
        public int[] readRawPackedVarInt32() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int[] readRawPackedZigZagVarInt32() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long[] readRawPackedVarInt64() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long[] readRawPackedZigZagVarInt64() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean[] readRawPackedBool() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int[] readRawPackedFixedInt32() {
            var length = readRawVarInt32();
            var segment = readRawMemorySegment(length);
            return segment.toArray(FIXED_INT32_LAYOUT);
        }

        @Override
        public long[] readRawPackedFixedInt64() {
            var length = readRawVarInt32();
            var segment = readRawMemorySegment(length);
            return segment.toArray(FIXED_INT64_LAYOUT);
        }
    }
}
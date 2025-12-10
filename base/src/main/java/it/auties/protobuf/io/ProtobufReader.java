// Protocol Buffers - Google's data interchange format
// Copyright 2008 Google Inc.  All rights reserved.
//
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file or at
// https://developers.google.com/open-source/licenses/bsd

// I'm not sure if the LICENSE copyright header is necessary as only two methods in this class are taken from Google's source code
// But just to be sure I included it

package it.auties.protobuf.io;

import it.auties.protobuf.exception.ProtobufDeserializationException;
import it.auties.protobuf.model.ProtobufUnknownValue;
import it.auties.protobuf.model.ProtobufWireType;
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorOperators;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Objects;

/**
 * An abstract input stream for reading Protocol Buffer encoded data.
 * <p>
 * This class provides a comprehensive API for deserializing Protocol Buffer messages from various
 * data sources including byte arrays, ByteBuffers, and InputStreams. It supports all Protocol Buffer
 * wire types and provides both type-safe and unchecked reading methods.
 * <p>
 *
 * @see ProtobufWriter
 */
public abstract non-sealed class ProtobufReader extends ProtobufIO {
    protected int wireType;
    protected long index;
    protected ProtobufReader() {
        resetPropertyTag();
    }

    public static ProtobufReader fromBytes(byte[] bytes) {
        return new ByteArrayReader(bytes, 0, bytes.length);
    }

    public static ProtobufReader fromBytes(byte[] bytes, int offset, int length) {
        return new ByteArrayReader(bytes, offset, offset + length);
    }

    public static ProtobufReader fromBuffer(ByteBuffer buffer) {
        return new ByteBufferReader(buffer);
    }

    public static ProtobufReader fromStream(InputStream buffer) {
        return new InputStreamReader(buffer, true);
    }

    public static ProtobufReader fromStream(InputStream buffer, boolean autoclose) {
        return new InputStreamReader(buffer, autoclose);
    }

    public static ProtobufReader fromMemorySegment(MemorySegment segment) {
        return new MemorySegmentReader(segment);
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

    public ProtobufReader readLengthDelimitedProperty() {
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
            var result = readRawFloat();
            resetPropertyTag();
            return result;
        }
    }

    public double readDoubleProperty() {
        if(wireType != ProtobufWireType.WIRE_TYPE_FIXED64) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        } else {
            var result = readRawDouble();
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
        return readFixed32();
    }

    public int readSFixed32Property() {
        return readFixed32();
    }

    private int readFixed32() {
        if(wireType != ProtobufWireType.WIRE_TYPE_FIXED32) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        } else {
            var result = readRawFixedInt32();
            resetPropertyTag();
            return result;
        }
    }

    public long readFixed64Property() {
        return readFixed64();
    }

    public long readSFixed64Property() {
        return readFixed64();
    }

    private long readFixed64() {
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
                yield switch (rawDataTypePreference()) {
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
                    var value = readUnknownProperty();
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
            case ProtobufWireType.WIRE_TYPE_FIXED32 -> new float[]{readRawFloat()};
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
        resetPropertyTag();
        return result;
    }

    public double[] readPackedDoubleProperty() {
        var result = switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> readRawPackedDouble();
            case ProtobufWireType.WIRE_TYPE_FIXED64 -> new double[]{readRawDouble()};
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
        return readPackedFixed32();
    }

    public int[] readPackedSFixed32Property() {
        return readPackedFixed32();
    }

    private int[] readPackedFixed32() {
        var result = switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> readRawPackedFixedInt32();
            case ProtobufWireType.WIRE_TYPE_FIXED32 -> new int[]{readRawFixedInt32()};
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
        resetPropertyTag();
        return result;
    }

    public long[] readPackedFixed64Property() {
        return readPackedFixed64();
    }

    public long[] readPackedSFixed64Property() {
        return  readPackedFixed64();
    }

    private long[] readPackedFixed64() {
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
                    skipUnknownProperty();
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

    public int readRawZigZagVarInt32() {
        var value = readRawVarInt32();
        var unsigned = Integer.toUnsignedLong(value);
        return (int) ((unsigned >> 1) ^ (-(unsigned & 1)));
    }

    public long readRawZigZagVarInt64() {
        var value = readRawVarInt64();
        return (value >>> 1) ^ (-(value & 1));
    }

    public abstract void skipRawBytes(int size);
    public abstract byte readRawByte();

    public abstract byte[] readRawBytes(int size);
    public abstract ByteBuffer readRawBuffer(int size);
    public abstract MemorySegment readRawMemorySegment(int size);

    public abstract int readRawFixedInt32();
    public abstract long readRawFixedInt64();
    public abstract float readRawFloat();
    public abstract double readRawDouble();
    public abstract int readRawVarInt32();
    public abstract long readRawVarInt64();

    public abstract float[] readRawPackedFloat();
    public abstract double[] readRawPackedDouble();
    public abstract int[] readRawPackedVarInt32();
    public abstract int[] readRawPackedZigZagVarInt32();
    public abstract long[] readRawPackedVarInt64();
    public abstract long[] readRawPackedZigZagVarInt64();
    public abstract boolean[] readRawPackedBool();
    public abstract int[] readRawPackedFixedInt32();
    public abstract long[] readRawPackedFixedInt64();

    public abstract ProtobufReader readRawLengthDelimited(int size);

    public abstract boolean isFinished();

    private static final class ByteArrayReader extends ProtobufReader {
        private final byte[] buffer;
        private final int limit;
        private int offset;

        ByteArrayReader(byte[] buffer, int offset, int limit) {
            Objects.requireNonNull(buffer, "buffer cannot be null");
            Objects.checkFromToIndex(offset, limit, buffer.length);
            this.buffer = buffer;
            this.offset = offset;
            this.limit = limit;
        }

        @Override
        public byte readRawByte() {
            if (offset >= limit) {
                throw ProtobufDeserializationException.truncatedMessage();
            } else {
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
            } catch (IndexOutOfBoundsException error) {
                throw ProtobufDeserializationException.truncatedMessage();
            }
        }

        @Override
        public ByteBuffer readRawBuffer(int size) {
            try {
                var result = ByteBuffer.wrap(buffer, offset, size);
                offset += size;
                return result;
            } catch (IndexOutOfBoundsException error) {
                if (size < 0) {
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
            } catch (IndexOutOfBoundsException error) {
                if (size < 0) {
                    throw ProtobufDeserializationException.negativeLength(size);
                } else {
                    throw ProtobufDeserializationException.truncatedMessage();
                }
            }
        }

        @Override
        public DataType rawDataTypePreference() {
            return DataType.BYTE_ARRAY;
        }

        @Override
        public boolean isFinished() {
            return offset >= limit;
        }

        @Override
        public void skipRawBytes(int size) {
            if (size < 0) {
                throw new IllegalArgumentException("size cannot be negative");
            } else {
                offset += size;
                if (offset > limit) {
                    throw ProtobufDeserializationException.truncatedMessage();
                }
            }
        }

        @Override
        public int readRawFixedInt32() {
            if (offset + Integer.BYTES > limit) {
                throw ProtobufDeserializationException.truncatedMessage();
            } else {
                var result = getIntLE(buffer, offset);
                offset += Integer.BYTES;
                return result;
            }
        }

        @Override
        public long readRawFixedInt64() {
            if (offset + Long.BYTES > limit) {
                throw ProtobufDeserializationException.truncatedMessage();
            } else {
                var result = getLongLE(buffer, offset);
                offset += Long.BYTES;
                return result;
            }
        }

        @Override
        public float readRawFloat() {
            if (offset + Float.BYTES > limit) {
                throw ProtobufDeserializationException.truncatedMessage();
            } else {
                var result = getFloatLE(buffer, offset);
                offset += Float.BYTES;
                return result;
            }
        }

        @Override
        public double readRawDouble() {
            if (offset + Double.BYTES > limit) {
                throw ProtobufDeserializationException.truncatedMessage();
            } else {
                var result = getDoubleLE(buffer, offset);
                offset += Double.BYTES;
                return result;
            }
        }

        @Override
        public ByteArrayReader readRawLengthDelimited(int size) {
            try {
                var result = new ByteArrayReader(buffer, offset, offset + size);
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
            var value = getLongLE(buffer, offset);
            var mostSignificantBits = ~value & ~INT64_PEXT_MASK_LOW;
            var lengthInBits = Long.numberOfTrailingZeros(mostSignificantBits) + 1;
            var varIntPart = value & (mostSignificantBits ^ (mostSignificantBits - 1));
            var result = (int) Long.compress(varIntPart, INT32_PEXT_MASK);
            offset += lengthInBits >>> 3;
            return result;
        }

        @Override
        public long readRawVarInt64() {
            var b0 = getLongLE(buffer, offset);
            var b1 = getLongLE(buffer, offset + 8);

            var msbB0 = ~b0 & ~INT64_PEXT_MASK_LOW;
            var msbB1 = ~b1 & ~INT64_PEXT_MASK_LOW;

            var lenB0 = Long.numberOfTrailingZeros(msbB0) + 1;
            var lenB1 = Long.numberOfTrailingZeros(msbB1) + 1;

            var partB0 = b0 & (msbB0 ^ (msbB0 - 1));
            var partB1 = (b1 & (msbB1 ^ (msbB1 - 1))) * ((msbB0 == 0) ? 1L : 0L);

            var vectorBytes = new byte[16];
            putLongLE(vectorBytes, 0, partB0);
            putLongLE(vectorBytes, 8, partB1);

            var x = getLongLE(vectorBytes, 0);
            var y = getLongLE(vectorBytes, 8);

            var result = Long.compress(x, INT64_PEXT_MASK_LOW)
                         | (Long.compress(y, INT64_PEXT_MASK_HIGH) << 56);

            offset += (msbB0 == 0 ? lenB1 + 64 : lenB0) >>> 3;

            return result;
        }

        @Override
        public float[] readRawPackedFloat() {
            var length = readRawVarInt32();
            var buffer = ByteBuffer.wrap(this.buffer, offset, offset + length)
                    .asFloatBuffer();
            var result = new float[buffer.remaining()];
            buffer.get(result);
            return result;
        }

        @Override
        public double[] readRawPackedDouble() {
            var length = readRawVarInt32();
            var buffer = ByteBuffer.wrap(this.buffer, offset, offset + length)
                    .asDoubleBuffer();
            var result = new double[buffer.remaining()];
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
            var result = new int[buffer.remaining()];
            buffer.get(result);
            return result;
        }

        @Override
        public long[] readRawPackedFixedInt64() {
            var length = readRawVarInt32();
            var buffer = ByteBuffer.wrap(this.buffer, offset, offset + length)
                    .asLongBuffer();
            var result = new long[buffer.remaining()];
            buffer.get(result);
            return result;
        }
    }

    private static final class ByteBufferReader extends ProtobufReader {
        private final ByteBuffer buffer;

        ByteBufferReader(ByteBuffer buffer) {
            Objects.requireNonNull(buffer, "buffer cannot be null");
            this.buffer = buffer.duplicate()
                    .order(ByteOrder.LITTLE_ENDIAN);
        }

        @Override
        public byte readRawByte() {
            try {
                return buffer.get();
            } catch (BufferUnderflowException _) {
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
            } catch (IndexOutOfBoundsException _) {
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
            } catch (IndexOutOfBoundsException _) {
                throw ProtobufDeserializationException.truncatedMessage();
            }
        }

        @Override
        public DataType rawDataTypePreference() {
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
            } catch (BufferUnderflowException _) {
                throw ProtobufDeserializationException.truncatedMessage();
            }
        }

        @Override
        public long readRawFixedInt64() {
            try {
                return buffer.getLong();
            } catch (BufferUnderflowException _) {
                throw ProtobufDeserializationException.truncatedMessage();
            }
        }

        @Override
        public float readRawFloat() {
            try {
                return buffer.getFloat();
            } catch (BufferUnderflowException _) {
                throw ProtobufDeserializationException.truncatedMessage();
            }
        }

        @Override
        public double readRawDouble() {
            try {
                return buffer.getDouble();
            } catch (BufferUnderflowException _) {
                throw ProtobufDeserializationException.truncatedMessage();
            }
        }

        @Override
        public ByteBufferReader readRawLengthDelimited(int size) {
            try {
                var position = buffer.position();
                var result = new ByteBufferReader(buffer.slice(position, size));
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
            var value = getLongLE(buffer, buffer.position());
            var mostSignificantBits = ~value & ~INT64_PEXT_MASK_LOW;
            var lengthInBits = Long.numberOfTrailingZeros(mostSignificantBits) + 1;
            var varIntPart = value & (mostSignificantBits ^ (mostSignificantBits - 1));
            var result = (int) Long.compress(varIntPart, INT32_PEXT_MASK);
            buffer.position(lengthInBits >>> 3);
            return result;
        }

        @Override
        public long readRawVarInt64() {
            var b0 = getLongLE(buffer, buffer.position());
            var b1 = getLongLE(buffer, buffer.position() + 8);

            var msbB0 = ~b0 & ~INT64_PEXT_MASK_LOW;
            var msbB1 = ~b1 & ~INT64_PEXT_MASK_LOW;

            var lenB0 = Long.numberOfTrailingZeros(msbB0) + 1;
            var lenB1 = Long.numberOfTrailingZeros(msbB1) + 1;

            var partB0 = b0 & (msbB0 ^ (msbB0 - 1));
            var partB1 = (b1 & (msbB1 ^ (msbB1 - 1))) * ((msbB0 == 0) ? 1L : 0L);

            var vectorBytes = new byte[16];
            putLongLE(vectorBytes, 0, partB0);
            putLongLE(vectorBytes, 8, partB1);

            var x = getLongLE(vectorBytes, 0);
            var y = getLongLE(vectorBytes, 8);

            var result = Long.compress(x, INT64_PEXT_MASK_LOW)
                         | (Long.compress(y, INT64_PEXT_MASK_HIGH) << 56);

            buffer.position((msbB0 == 0 ? lenB1 + 64 : lenB0) >>> 3);

            return result;
        }

        @Override
        public float[] readRawPackedFloat() {
            var length = readRawVarInt32();
            var segment = readRawMemorySegment(length);
            return toFloatArrayLE(segment);
        }

        @Override
        public double[] readRawPackedDouble() {
            var length = readRawVarInt32();
            var segment = readRawMemorySegment(length);
            return toDoubleArrayLE(segment);
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
            return toIntArrayLE(segment);
        }

        @Override
        public long[] readRawPackedFixedInt64() {
            var length = readRawVarInt32();
            var segment = readRawMemorySegment(length);
            return toLongArrayLE(segment);
        }
    }

    private static final class MemorySegmentReader extends ProtobufReader {
        private final MemorySegment segment;
        private int position;

        MemorySegmentReader(MemorySegment segment) {
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
            } catch (BufferUnderflowException _) {
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
            } catch (IndexOutOfBoundsException _) {
                throw ProtobufDeserializationException.truncatedMessage();
            }
        }

        @Override
        public MemorySegment readRawMemorySegment(int size) {
            try {
                var result = segment.asSlice(position, position + size);
                position += size;
                return result;
            } catch (IndexOutOfBoundsException _) {
                throw ProtobufDeserializationException.truncatedMessage();
            }
        }

        @Override
        public DataType rawDataTypePreference() {
            return DataType.MEMORY_SEGMENT;
        }

        @Override
        public boolean isFinished() {
            return position >= segment.byteSize();
        }

        @Override
        public void skipRawBytes(int size) {
            position += size;
            if (position > size) {
                throw ProtobufDeserializationException.truncatedMessage();
            }
        }

        @Override
        public int readRawFixedInt32() {
            var result = segment.getAtIndex(ValueLayout.OfInt.JAVA_INT_UNALIGNED, position);
            position += Integer.BYTES;
            return result;
        }

        @Override
        public long readRawFixedInt64() {
            var result = segment.getAtIndex(ValueLayout.OfLong.JAVA_LONG_UNALIGNED, position);
            position += Long.BYTES;
            return result;
        }

        @Override
        public float readRawFloat() {
            var result = segment.getAtIndex(ValueLayout.OfInt.JAVA_FLOAT_UNALIGNED, position);
            position += Float.BYTES;
            return result;
        }

        @Override
        public double readRawDouble() {
            var result = segment.getAtIndex(ValueLayout.OfInt.JAVA_DOUBLE_UNALIGNED, position);
            position += Double.BYTES;
            return result;
        }

        @Override
        public MemorySegmentReader readRawLengthDelimited(int size) {
            var result = new MemorySegmentReader(segment.asSlice(position, size));
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
        public float[] readRawPackedFloat() {
            var length = readRawVarInt32();
            var segment = readRawMemorySegment(length);
            return toFloatArrayLE(segment);
        }

        @Override
        public double[] readRawPackedDouble() {
            var length = readRawVarInt32();
            var segment = readRawMemorySegment(length);
            return toDoubleArrayLE(segment);
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
            return toIntArrayLE(segment);
        }

        @Override
        public long[] readRawPackedFixedInt64() {
            var length = readRawVarInt32();
            var segment = readRawMemorySegment(length);
            return toLongArrayLE(segment);
        }
    }

    private static final class InputStreamReader extends ProtobufReader {
        private static final int MAX_VAR_INT_SIZE = 10;

        private final InputStream inputStream;
        private final boolean autoclose;
        private final long length;
        private long position;

        private final byte[] buffer;
        private int bufferPosition;
        private int bufferLength;

        InputStreamReader(InputStream inputStream, boolean autoclose) {
            Objects.requireNonNull(inputStream, "inputStream cannot be null");
            this.inputStream = inputStream;
            this.autoclose = autoclose;
            this.length = -1;
            this.buffer = new byte[MAX_VAR_INT_SIZE];
        }

        private InputStreamReader(InputStream inputStream, long length, byte[] buffer, int bufferPosition, int bufferLength) {
            this.inputStream = inputStream;
            this.autoclose = false;
            this.length = length;
            this.buffer = buffer;
            this.bufferPosition = bufferPosition;
            this.bufferLength = bufferLength;
        }

        @Override
        public byte readRawByte() {
            position++;
            if (bufferPosition < bufferLength) {
                return buffer[bufferPosition++];
            } else {
                try {
                    var read = inputStream.read();
                    if (read == -1) {
                        throw ProtobufDeserializationException.truncatedMessage();
                    } else {
                        return (byte) read;
                    }
                } catch (IOException exception) {
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
            } catch (NegativeArraySizeException _) {
                throw new IllegalArgumentException("size cannot be negative");
            }
        }

        private void readRawBytes(byte[] output, int length) {
            position += length;
            var offset = 0;
            if (length > 0 && bufferPosition < bufferLength) {
                var bufferedLength = Math.min(bufferLength - bufferPosition, length);
                System.arraycopy(buffer, bufferPosition, output, offset, bufferedLength);
                offset += bufferedLength;
                bufferPosition += bufferedLength;
            }
            while (offset < length) {
                try {
                    var read = inputStream.read(output, offset, length - offset);
                    if (read == -1) {
                        throw ProtobufDeserializationException.truncatedMessage();
                    } else {
                        offset += read;
                    }
                } catch (IOException exception) {
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
        public DataType rawDataTypePreference() {
            return DataType.BYTE_ARRAY;
        }

        @Override
        public boolean isFinished() {
            if (length != -1) {
                return position >= length;
            } else if (bufferPosition < bufferLength) {
                return false;
            } else {
                try {
                    position++;
                    var read = inputStream.read();
                    if (read == -1) {
                        return true;
                    } else {
                        position--;
                        buffer[bufferPosition = 0] = (byte) read;
                        bufferLength = 1;
                        return false;
                    }
                } catch (IOException exception) {
                    throw ProtobufDeserializationException.truncatedMessage(exception);
                }
            }
        }

        @Override
        public void skipRawBytes(int length) {
            if (length < 0) {
                throw new IllegalArgumentException("length cannot be negative");
            }

            if (length > 0 && bufferPosition < bufferLength) {
                var bufferedLength = Math.min(length, bufferLength - bufferPosition);
                length -= bufferedLength;
                position += bufferedLength;
            }
            while (length > 0) {
                try {
                    var skipped = inputStream.skip(length);
                    if (skipped == 0) {
                        if (isFinished()) {
                            throw ProtobufDeserializationException.truncatedMessage();
                        }
                    } else {
                        length -= (int) skipped;
                        position += skipped;
                    }
                } catch (IOException exception) {
                    throw ProtobufDeserializationException.truncatedMessage(exception);
                }
            }
        }

        @Override
        public int readRawFixedInt32() {
            readRawBytes(buffer, Integer.BYTES);
            return getIntLE(buffer, 0);
        }

        @Override
        public long readRawFixedInt64() {
            readRawBytes(buffer, Long.BYTES);
            return getLongLE(buffer, 0);
        }

        @Override
        public float readRawFloat() {
            readRawBytes(buffer, Float.BYTES);
            return getFloatLE(buffer, 0);
        }

        @Override
        public double readRawDouble() {
            readRawBytes(buffer, Double.BYTES);
            return getDoubleLE(buffer, 0);
        }

        @Override
        public InputStreamReader readRawLengthDelimited(int size) {
            var result = new InputStreamReader(inputStream, size, buffer, bufferPosition, bufferLength);
            position += size;
            return result;
        }

        @Override
        public void close() throws IOException {
            if (autoclose) {
                inputStream.close();
            }
        }

        @Override
        public int readRawVarInt32() {
            readRawBytes(buffer, MAX_VAR_INT_SIZE);
            var value = getLongLE(buffer, 0);
            var mostSignificantBits = ~value & ~INT64_PEXT_MASK_LOW;
            var lengthInBits = Long.numberOfTrailingZeros(mostSignificantBits) + 1;
            var varIntPart = value & (mostSignificantBits ^ (mostSignificantBits - 1));
            var result = (int) Long.compress(varIntPart, INT32_PEXT_MASK);
            var length = lengthInBits >>> 3;
            bufferPosition = length;
            bufferLength = MAX_VAR_INT_SIZE;
            position += length;
            return result;
        }

        @Override
        public long readRawVarInt64() {
            readRawBytes(buffer, MAX_VAR_INT_SIZE);
            var b0 = getLongLE(buffer, 0);
            var b1 = getLongLE(buffer, 8);
            var msbB0 = ~b0 & ~INT64_PEXT_MASK_LOW;
            var msbB1 = ~b1 & ~INT64_PEXT_MASK_LOW;
            var lenB0 = Long.numberOfTrailingZeros(msbB0) + 1;
            var lenB1 = Long.numberOfTrailingZeros(msbB1) + 1;
            var partB0 = b0 & (msbB0 ^ (msbB0 - 1));
            var partB1 = (b1 & (msbB1 ^ (msbB1 - 1))) * ((msbB0 == 0) ? 1L : 0L);
            var vectorBytes = new byte[16];
            putLongLE(vectorBytes, 0, partB0);
            putLongLE(vectorBytes, 8, partB1);
            var x = getLongLE(vectorBytes, 0);
            var y = getLongLE(vectorBytes, 8);
            var result = Long.compress(x, INT64_PEXT_MASK_LOW) | (Long.compress(y, INT64_PEXT_MASK_HIGH) << 56);
            var length = (msbB0 == 0 ? lenB1 + 64 : lenB0) >>> 3;
            bufferPosition = length;
            bufferLength = MAX_VAR_INT_SIZE;
            position += length;
            return result;
        }

        @Override
        public float[] readRawPackedFloat() {
            var length = readRawVarInt32();
            var segment = readRawMemorySegment(length);
            return toFloatArrayLE(segment);
        }

        @Override
        public double[] readRawPackedDouble() {
            var length = readRawVarInt32();
            var segment = readRawMemorySegment(length);
            return toDoubleArrayLE(segment);
        }

        @Override
        public int[] readRawPackedVarInt32() {
            var bufferLength = readRawVarInt32();
            var buffer = readRawBytes(bufferLength);

            var resultsCount = countVarInts(buffer);
            var results = new int[resultsCount];

            throw new UnsupportedOperationException();
        }

        private int countVarInts(byte[] segment) {
            var len = segment.length;
            var count = 0;
            var i = 0;

            if (SUPPORTS_V512) {
                long bound512 = V512.loopBound(len);
                for (; i < bound512; i += V512.length()) {
                    count += ByteVector.fromArray(V512, segment, i)
                            .compare(VectorOperators.GE, (byte) 0)
                            .trueCount();
                }
            }

            if(SUPPORTS_V256) {
                long bound256 = i + V256.loopBound(len - i);
                for (; i < bound256; i += V256.length()) {
                    count += ByteVector.fromArray(V256, segment, i)
                            .compare(VectorOperators.GE, (byte) 0)
                            .trueCount();
                }
            }

            if(SUPPORTS_V128) {
                long bound128 = i + V128.loopBound(len - i);
                for (; i < bound128; i += V128.length()) {
                    count += ByteVector.fromArray(V128, segment, i)
                            .compare(VectorOperators.GE, (byte) 0)
                            .trueCount();
                }
            }

            if(SUPPORTS_V64) {
                long bound64 = i + V64.loopBound(len - i);
                for (; i < bound64; i += V64.length()) {
                    count += ByteVector.fromArray(V64, segment, i)
                            .compare(VectorOperators.GE, (byte) 0)
                            .trueCount();
                }
            }

            for (; i < len; i++) {
                if (segment[i] >= 0) {
                    count++;
                }
            }

            return count;
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
            return toIntArrayLE(segment);
        }

        @Override
        public long[] readRawPackedFixedInt64() {
            var length = readRawVarInt32();
            var segment = readRawMemorySegment(length);
            return toLongArrayLE(segment);
        }
    }
}
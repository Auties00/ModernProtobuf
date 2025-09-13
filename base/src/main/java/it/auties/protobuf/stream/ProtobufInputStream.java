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
import it.auties.protobuf.model.ProtobufString;
import it.auties.protobuf.model.ProtobufWireType;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.InvalidMarkException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An abstract input stream for reading Protocol Buffer encoded data.
 * <p>
 * This class provides a comprehensive API for deserializing Protocol Buffer messages from various
 * data sources including byte arrays, ByteBuffers, and InputStreams. It supports all Protocol Buffer
 * wire types and provides both type-safe and unchecked reading methods.
 * <p>
 * The class implements a tag-based reading pattern where {@link #readTag()} is called first to
 * identify the field number and wire type, followed by the appropriate read method based on the
 * expected data type.
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * try (ProtobufInputStream input = ProtobufInputStream.fromBytes(data)) {
 *     while (input.readTag()) {
 *         switch (input.index()) {
 *             case 1 -> input.readString();
 *             case 2 -> input.readInt32();
 *             default -> input.skipUnknown();
 *         };
 *     }
 * }
 * }</pre>
 *
 * @see ProtobufOutputStream
 * @see AutoCloseable
 */
@SuppressWarnings("unused")
public abstract class ProtobufInputStream implements AutoCloseable {
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);
    private static final byte[] EMPTY_BYTES = new byte[0];

    private int wireType;
    private long index;
    protected ProtobufInputStream() {
        this.wireType = -1;
        this.index = -1;
    }

    public static ProtobufInputStream fromBytes(byte[] bytes) {
        return new Bytes(bytes, 0, bytes.length);
    }

    public static ProtobufInputStream fromBytes(byte[] bytes, int offset, int length) {
        return new Bytes(bytes, offset, length);
    }

    public static ProtobufInputStream fromBuffer(ByteBuffer buffer) {
        return new Buffer(buffer);
    }

    public static ProtobufInputStream fromStream(InputStream buffer) {
        return new Stream(buffer, true);
    }

    public static ProtobufInputStream fromStream(InputStream buffer, boolean autoclose) {
        return new Stream(buffer, autoclose);
    }

    public boolean readTag() {
        if(isFinished()) {
            return false;
        }

        var rawTag = readInt32Unchecked();
        this.wireType = rawTag & 7;
        this.index = rawTag >>> 3;
        if(index == 0) {
            throw ProtobufDeserializationException.invalidFieldIndex(index);
        }
        return wireType != ProtobufWireType.WIRE_TYPE_END_OBJECT;
    }

    public List<Float> readFloatPacked() {
        return switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> {
                var results = new ArrayList<Float>();
                try(var input = readLengthDelimited()) {
                    while (!input.isFinished()) {
                        results.add(input.readFloatUnchecked());
                    }
                }catch (Exception exception) {
                    throw new ProtobufDeserializationException(exception);
                }

                yield results;
            }

            case ProtobufWireType.WIRE_TYPE_FIXED32 -> List.of(readFloatUnchecked());
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
    }

    public List<Double> readDoublePacked() {
        return switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> {
                var results = new ArrayList<Double>();
                try(var input = readLengthDelimited()) {
                    while (!input.isFinished()) {
                        results.add(input.readDoubleUnchecked());
                    }
                }catch (Exception exception) {
                    throw new ProtobufDeserializationException(exception);
                }

                yield results;
            }

            case ProtobufWireType.WIRE_TYPE_FIXED64 -> List.of(readDoubleUnchecked());
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
    }

    public List<Integer> readInt32Packed() {
        return switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> {
                var results = new ArrayList<Integer>();
                try(var input = readLengthDelimited()) {
                    while (!input.isFinished()) {
                        results.add(input.readInt32Unchecked());
                    }
                }catch (Exception exception) {
                    throw new ProtobufDeserializationException(exception);
                }

                yield results;
            }

            case ProtobufWireType.WIRE_TYPE_VAR_INT -> List.of(readInt32Unchecked());
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
    }

    public List<Long> readInt64Packed() {
        return switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> {
                var results = new ArrayList<Long>();
                try(var input = readLengthDelimited()) {
                    while (!input.isFinished()) {
                        results.add(input.readInt64Unchecked());
                    }
                }catch (Exception exception) {
                    throw new ProtobufDeserializationException(exception);
                }

                yield results;
            }

            case ProtobufWireType.WIRE_TYPE_VAR_INT -> List.of(readInt64Unchecked());
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
    }

    public List<Integer> readFixed32Packed() {
        return switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> {
                var results = new ArrayList<Integer>();
                try(var input = readLengthDelimited()) {
                    while (!input.isFinished()) {
                        results.add(input.readFixed32Unchecked());
                    }
                }catch (Exception exception) {
                    throw new ProtobufDeserializationException(exception);
                }

                yield results;
            }

            case ProtobufWireType.WIRE_TYPE_FIXED32 -> List.of(readFixed32Unchecked());
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
    }

    public List<Long> readFixed64Packed() {
        return switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> {
                var results = new ArrayList<Long>();
                try(var input = readLengthDelimited()) {
                    while (!input.isFinished()) {
                        results.add(input.readFixed64Unchecked());
                    }
                }catch (Exception exception) {
                    throw new ProtobufDeserializationException(exception);
                }

                yield results;
            }

            case ProtobufWireType.WIRE_TYPE_FIXED64 -> List.of(readFixed64Unchecked());
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
    }

    public List<Boolean> readBoolPacked(){
        return switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> {
                var results = new ArrayList<Boolean>();
                try(var input = readLengthDelimited()) {
                    while (!input.isFinished()) {
                        results.add(input.readBoolUnchecked());
                    }
                }catch (Exception exception) {
                    throw new ProtobufDeserializationException(exception);
                }

                yield results;
            }

            case ProtobufWireType.WIRE_TYPE_VAR_INT -> List.of(readBoolUnchecked());
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
    }

    public float readFloat() {
        return Float.intBitsToFloat(readFixed32());
    }

    public float readFloatUnchecked() {
        return Float.intBitsToFloat(readFixed32());
    }

    public double readDouble() {
        return Double.longBitsToDouble(readFixed64());
    }

    public double readDoubleUnchecked() {
        return Double.longBitsToDouble(readFixed64Unchecked());
    }

    public boolean readBool() {
        if(wireType != ProtobufWireType.WIRE_TYPE_VAR_INT) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        }

        return readBoolUnchecked();
    }

    private boolean readBoolUnchecked() {
        return readInt64() == 1;
    }

    public ProtobufString.Lazy readString() {
        if(wireType != ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        }

        var size = this.readInt32Unchecked();
        if(size < 0) {
            throw ProtobufDeserializationException.negativeLength(size);
        }else {
            return readString(size);
        }
    }

    public int readInt32() {
        if(wireType != ProtobufWireType.WIRE_TYPE_VAR_INT) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        }

        return readInt32Unchecked();
    }

    // Source: https://github.com/protocolbuffers/protobuf/blob/main/java/core/src/main/java/com/google/protobuf/CodedInputStream.java
    // Fastest implementation I could find
    // Adapted to work with Channels
    private int readInt32Unchecked() {
        mark();
        fspath:
        {
            int x;
            if ((x = readByte()) >= 0) {
                return x;
            } else if ((x ^= (readByte() << 7)) < 0) {
                x ^= (~0 << 7);
            } else if ((x ^= (readByte() << 14)) >= 0) {
                x ^= (~0 << 7) ^ (~0 << 14);
            } else if ((x ^= (readByte() << 21)) < 0) {
                x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21);
            } else {
                int y = readByte();
                x ^= y << 28;
                x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21) ^ (~0 << 28);
                if (y < 0
                        && readByte() < 0
                        && readByte() < 0
                        && readByte() < 0
                        && readByte() < 0
                        && readByte() < 0) {
                    break fspath;
                }
            }
            return x;
        }

        rewind();
        return (int) readVarInt64Slow();
    }

    public long readInt64() {
        if(wireType != ProtobufWireType.WIRE_TYPE_VAR_INT) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        }

        return readInt64Unchecked();
    }

    // Source: https://github.com/protocolbuffers/protobuf/blob/main/java/core/src/main/java/com/google/protobuf/CodedInputStream.java
    // Fastest implementation I could find
    // Adapted to work with Channels
    private long readInt64Unchecked() {
        mark();
        fspath:
        {
            long x;
            int y;
            if ((y = readByte()) >= 0) {
                return y;
            } else if ((y ^= (readByte() << 7)) < 0) {
                x = y ^ (~0 << 7);
            } else if ((y ^= (readByte() << 14)) >= 0) {
                x = y ^ ((~0 << 7) ^ (~0 << 14));
            } else if ((y ^= (readByte() << 21)) < 0) {
                x = y ^ ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
            } else if ((x = y ^ ((long) readByte() << 28)) >= 0L) {
                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28);
            } else if ((x ^= ((long) readByte() << 35)) < 0L) {
                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35);
            } else if ((x ^= ((long) readByte() << 42)) >= 0L) {
                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42);
            } else if ((x ^= ((long) readByte() << 49)) < 0L) {
                x ^=
                        (~0L << 7)
                                ^ (~0L << 14)
                                ^ (~0L << 21)
                                ^ (~0L << 28)
                                ^ (~0L << 35)
                                ^ (~0L << 42)
                                ^ (~0L << 49);
            } else {
                x ^= ((long) readByte() << 56);
                x ^=
                        (~0L << 7)
                                ^ (~0L << 14)
                                ^ (~0L << 21)
                                ^ (~0L << 28)
                                ^ (~0L << 35)
                                ^ (~0L << 42)
                                ^ (~0L << 49)
                                ^ (~0L << 56);
                if (x < 0L) {
                    if (readByte() < 0L) {
                        break fspath;
                    }
                }
            }
            return x;
        }

        rewind();
        return readVarInt64Slow();
    }

    private long readVarInt64Slow() {
        var result = 0L;
        for (var shift = 0; shift < 64; shift += 7) {
            var b = readByte();
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
        }

        throw ProtobufDeserializationException.malformedVarInt();
    }

    public int readFixed32() {
        if(wireType != ProtobufWireType.WIRE_TYPE_FIXED32) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        }

        return readFixed32Unchecked();
    }

    private int readFixed32Unchecked() {
        return readByte() & 255
                | (readByte() & 255) << 8
                | (readByte() & 255) << 16
                | (readByte() & 255) << 24;
    }

    public long readFixed64() {
        if(wireType != ProtobufWireType.WIRE_TYPE_FIXED64) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        }

        return readFixed64Unchecked();
    }

    private long readFixed64Unchecked() {
        return (long) readByte() & 255L
                | ((long) readByte() & 255L) << 8
                | ((long) readByte() & 255L) << 16
                | ((long) readByte() & 255L) << 24
                | ((long) readByte() & 255L) << 32
                | ((long) readByte() & 255L) << 40
                | ((long) readByte() & 255L) << 48
                | ((long) readByte() & 255L) << 56;
    }

    public ByteBuffer readBytes() {
        if(wireType != ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        }

        var size = this.readInt32Unchecked();
        if(size < 0) {
            throw ProtobufDeserializationException.negativeLength(size);
        }else if(size == 0) {
            return EMPTY_BUFFER;
        }else {
            return readBytes(size);
        }
    }

    public void skipUnknown() {
        switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_VAR_INT -> readInt64();
            case ProtobufWireType.WIRE_TYPE_FIXED32 -> readFixed32();
            case ProtobufWireType.WIRE_TYPE_FIXED64 -> readFixed64();
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> readBytes();
            case ProtobufWireType.WIRE_TYPE_START_OBJECT -> skipGroup();
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
    }

    private void skipGroup() {
        while (readTag()) {
            skipUnknown();
        }
        assertGroupClosed(index);
    }

    public Object readUnknown() {
        return switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_VAR_INT -> readInt64();
            case ProtobufWireType.WIRE_TYPE_FIXED32 -> readFixed32();
            case ProtobufWireType.WIRE_TYPE_FIXED64 -> readFixed64();
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> readBytes();
            case ProtobufWireType.WIRE_TYPE_START_OBJECT -> readGroup();
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
    }

    private Map<Long, Object> readGroup() {
        var group = new HashMap<Long, Object>();
        while (readTag()) {
            var value = readUnknown();
            group.put(index, value);
        }
        assertGroupClosed(index);
        return group;
    }

    public void assertGroupOpened(long groupIndex) {
        if((wireType == -1 && !readTag()) || wireType != ProtobufWireType.WIRE_TYPE_START_OBJECT || index != groupIndex) {
            throw ProtobufDeserializationException.invalidStartObject(groupIndex);
        }
    }

    public void assertGroupClosed(long groupIndex) {
        if(wireType != ProtobufWireType.WIRE_TYPE_END_OBJECT) {
            throw ProtobufDeserializationException.malformedGroup();
        }
        if(index != groupIndex) {
            throw ProtobufDeserializationException.invalidEndObject(index, groupIndex);
        }
    }

    public int wireType() {
        return wireType;
    }

    public long index() {
        return index;
    }

    public ProtobufInputStream readLengthDelimited() {
        if(wireType != ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        }

        var size = this.readInt32Unchecked();
        if(size < 0) {
            throw ProtobufDeserializationException.negativeLength(size);
        }else {
            return subStream(size);
        }
    }

    protected abstract byte readByte();
    protected abstract ByteBuffer readBytes(int size);
    protected abstract ProtobufString.Lazy readString(int size);
    protected abstract void mark();
    protected abstract void rewind();
    protected abstract boolean isFinished();
    protected abstract ProtobufInputStream subStream(int size);

    private static final class Stream extends ProtobufInputStream {
        private static final int MAX_VAR_INT_SIZE = 10;

        private final InputStream inputStream;
        private final boolean autoclose;
        private final long length;
        private long position;

        private final byte[] buffer;
        private int bufferReadPosition;
        private int bufferWritePosition;
        private int bufferLength;

        private Stream(InputStream inputStream, boolean autoclose) {
            this.inputStream = inputStream;
            this.autoclose = autoclose;
            this.length = -1;
            this.buffer = new byte[MAX_VAR_INT_SIZE];
        }

        private Stream(InputStream inputStream, long length, byte[] buffer, int bufferReadPosition, int bufferWritePosition, int bufferLength) {
            this.inputStream = inputStream;
            this.autoclose = false;
            this.length = length;
            this.buffer = buffer;
            this.bufferReadPosition = bufferReadPosition;
            this.bufferWritePosition = bufferWritePosition;
            this.bufferLength = bufferLength;
        }

        @Override
        public byte readByte() {
            try {
                if(length != -1) {
                    position++;
                }

                if(bufferLength > 0) {
                    bufferLength--;
                    return buffer[bufferReadPosition++];
                }

                var result = (byte) inputStream.read();
                buffer[bufferWritePosition++ % buffer.length] = result;
                return result;
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        @Override
        public ByteBuffer readBytes(int size) {
            try {
                return ByteBuffer.wrap(readStreamBytes(size));
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        @Override
        public ProtobufString.Lazy readString(int size) {
            try {
                return ProtobufString.lazy(readStreamBytes(size), 0, size);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        private byte[] readStreamBytes(int size) throws IOException {
            if (size == 0) {
                return EMPTY_BYTES;
            }

            if (length != -1) {
                position += size;
            }

            var result = new byte[size];
            var totalBytesRead = 0;

            var bytesFromBuffer = Math.min(size, bufferLength);
            if (bytesFromBuffer > 0) {
                System.arraycopy(buffer, bufferReadPosition, result, 0, bytesFromBuffer);
                totalBytesRead += bytesFromBuffer;
                bufferReadPosition += bytesFromBuffer;
                bufferLength -= bytesFromBuffer;
            }

            while (totalBytesRead < size) {
                var bytesReadFromStream = inputStream.read(result, totalBytesRead, size - totalBytesRead);
                if (bytesReadFromStream == -1) {
                    throw ProtobufDeserializationException.truncatedMessage();
                }

                for (var i = 0; i < bytesReadFromStream; i++) {
                    buffer[bufferWritePosition % buffer.length] = result[totalBytesRead + i];
                    bufferWritePosition++;
                }
                totalBytesRead += bytesReadFromStream;
            }

            return result;
        }

        @Override
        public void mark() {
            this.bufferReadPosition = 0;
            this.bufferWritePosition = 0;
        }

        @Override
        public void rewind() {
            this.bufferReadPosition = 0;
            this.bufferLength = bufferWritePosition - bufferReadPosition;
            if(length != -1) {
                this.position -= bufferLength;
            }
        }

        @Override
        public boolean isFinished() {
            if (length != -1) {
                return position >= length;
            }

            mark();
            var result = readByte() == -1;
            rewind();
            return result;
        }

        @Override
        public Stream subStream(int size) {
            var result = new Stream(inputStream, size, buffer, bufferReadPosition, bufferWritePosition, bufferLength);
            if(length != -1) {
                position += size;
            }
            return result;
        }

        @Override
        public void close() throws IOException {
            if(autoclose) {
                inputStream.close();
            }
        }
    }

    private static final class Bytes extends ProtobufInputStream {
        private final byte[] buffer;
        private final int offset;
        private final int length;
        private int position;
        private int marker;
        private Bytes(byte[] buffer, int offset, int length) {
            this.buffer = buffer;
            this.offset = offset;
            this.length = length;
        }

        @Override
        public byte readByte() {
            return buffer[offset + position++];
        }

        @Override
        public ByteBuffer readBytes(int size) {
            var result = ByteBuffer.wrap(buffer, offset + position, size);
            position += size;
            return result;
        }

        @Override
        public ProtobufString.Lazy readString(int size) {
            var result = ProtobufString.lazy(buffer, offset + position, size);
            position += size;
            return result;
        }

        @Override
        public void mark() {
            this.marker = position;
        }

        @Override
        public void rewind() {
            if(marker == -1) {
                throw new InvalidMarkException();
            }

            this.position = marker;
        }

        @Override
        public boolean isFinished() {
            return position >= length;
        }

        @Override
        public Bytes subStream(int size) {
            var result = new Bytes(buffer, offset + position, size);
            position += size;
            return result;
        }

        @Override
        public void close() {

        }
    }

    private static final class Buffer extends ProtobufInputStream {
        private final ByteBuffer buffer;
        private Buffer(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        @Override
        public byte readByte() {
            return buffer.get();
        }

        @Override
        public ByteBuffer readBytes(int size) {
            var position = buffer.position();
            var result = buffer.slice(position, size);
            buffer.position(position + size);
            return result;
        }

        @Override
        public ProtobufString.Lazy readString(int size) {
            var data = new byte[size];
            buffer.get(data);
            return ProtobufString.lazy(data);
        }

        @Override
        public void mark() {
            buffer.mark();
        }

        @Override
        public void rewind() {
            buffer.reset();
        }

        @Override
        public boolean isFinished() {
            return !buffer.hasRemaining();
        }

        @Override
        public Buffer subStream(int size) {
            var position = buffer.position();
            var result = new Buffer(buffer.slice(position, size));
            buffer.position(position + size);
            return result;
        }

        @Override
        public void close() {

        }
    }
}
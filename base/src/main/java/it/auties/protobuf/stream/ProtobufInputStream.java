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
import it.auties.protobuf.model.ProtobufLazyString;
import it.auties.protobuf.model.ProtobufUnknownValue;
import it.auties.protobuf.model.ProtobufWireType;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
    protected int wireType;
    protected long index;
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

    public int propertyWireType() {
        return wireType;
    }

    public long propertyIndex() {
        return index;
    }

    public boolean readPropertyTag() {
        if(isFinished()) {
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

    public int readLengthDelimitedPropertyLength() {
        var length = readRawVarInt32();
        if(length < 0) {
            throw ProtobufDeserializationException.negativeLength(length);
        } else {
            return length;
        }
    }

    public void readStartGroupProperty(long groupIndex) {
        if((wireType == -1 && !readPropertyTag()) || wireType != ProtobufWireType.WIRE_TYPE_START_OBJECT || index != groupIndex) {
            throw ProtobufDeserializationException.invalidStartObject(groupIndex);
        }
    }

    public void readEndGroupProperty(long groupIndex) {
        if(wireType != ProtobufWireType.WIRE_TYPE_END_OBJECT) {
            throw ProtobufDeserializationException.malformedGroup();
        } else if(index != groupIndex) {
            throw ProtobufDeserializationException.invalidEndObject(index, groupIndex);
        }
    }

    public float readFloatProperty() {
        if(wireType != ProtobufWireType.WIRE_TYPE_FIXED32) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        } else {
            return Float.intBitsToFloat(readRawFixedInt32());
        }
    }

    public List<Float> readFloatPackedProperty() {
        return switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> {
                var size = readLengthDelimitedPropertyLength();
                var results = new ArrayList<Float>(size / Float.BYTES);
                try(var input = readRawStream(size)) {
                    while (!input.isFinished()) {
                        results.add(Float.intBitsToFloat(input.readRawFixedInt32()));
                    }
                }catch (Exception exception) {
                    throw new ProtobufDeserializationException(exception);
                }

                yield results;
            }

            case ProtobufWireType.WIRE_TYPE_FIXED32 -> List.of(Float.intBitsToFloat(readRawFixedInt32()));
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
    }

    public double readDoubleProperty() {
        if(wireType != ProtobufWireType.WIRE_TYPE_FIXED64) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        } else {
            return Double.longBitsToDouble(readRawFixedInt64());
        }
    }

    public List<Double> readDoublePackedProperty() {
        return switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> {
                var size = readLengthDelimitedPropertyLength();
                var results = new ArrayList<Double>(size / Double.BYTES);
                try(var input = readRawStream(size)) {
                    while (!input.isFinished()) {
                        results.add(Double.longBitsToDouble(input.readRawFixedInt64()));
                    }
                }catch (Exception exception) {
                    throw new ProtobufDeserializationException(exception);
                }

                yield results;
            }

            case ProtobufWireType.WIRE_TYPE_FIXED64 -> List.of(Double.longBitsToDouble(readRawFixedInt64()));
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
    }

    public boolean readBoolProperty() {
        if(wireType != ProtobufWireType.WIRE_TYPE_VAR_INT) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        } else {
            return readRawVarInt64() == 1;
        }
    }

    public List<Boolean> readBoolPackedProperty(){
        return switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> {
                var size = readLengthDelimitedPropertyLength();
                var results = new ArrayList<Boolean>(size / Integer.BYTES);
                try(var input = readRawStream(size)) {
                    while (!input.isFinished()) {
                        results.add(readRawVarInt64() == 1);
                    }
                }catch (Exception exception) {
                    throw new ProtobufDeserializationException(exception);
                }

                yield results;
            }

            case ProtobufWireType.WIRE_TYPE_VAR_INT -> List.of(readRawVarInt64() == 1);
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
    }

    public ProtobufInputStream readLengthDelimitedPropertyAsStream() {
        if(wireType != ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        } else {
            var size = readLengthDelimitedPropertyLength();
            return readRawStream(size);
        }
    }

    public ProtobufLazyString readLengthDelimitedPropertyAsLazyString() {
        if(wireType != ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        } else {
            var size = readLengthDelimitedPropertyLength();
            return readRawLazyString(size);
        }
    }

    public String readLengthDelimitedPropertyAsDecodedString() {
        if(wireType != ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        } else {
            var size = readLengthDelimitedPropertyLength();
            return readRawDecodedString(size);
        }
    }

    public byte[] readLengthDelimitedPropertyAsBytes() {
        if(wireType != ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        } else {
            var size = readLengthDelimitedPropertyLength();
            return readRawBytes(size);
        }
    }

    public ByteBuffer readLengthDelimitedPropertyAsBuffer() {
        if(wireType != ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        } else {
            var size = readLengthDelimitedPropertyLength();
            return readRawBuffer(size);
        }
    }

    public int readInt32Property() {
        if(wireType != ProtobufWireType.WIRE_TYPE_VAR_INT) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        } else {
            return readRawVarInt32();
        }
    }

    public List<Integer> readInt32PackedProperty() {
        return switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> {
                var size = readLengthDelimitedPropertyLength();
                var results = new ArrayList<Integer>(size /  Integer.BYTES);
                try(var input = readRawStream(size)) {
                    while (!input.isFinished()) {
                        results.add(input.readRawVarInt32());
                    }
                }catch (Exception exception) {
                    throw new ProtobufDeserializationException(exception);
                }

                yield results;
            }

            case ProtobufWireType.WIRE_TYPE_VAR_INT -> List.of(readRawVarInt32());
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
    }

    public long readInt64() {
        if(wireType != ProtobufWireType.WIRE_TYPE_VAR_INT) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        } else {
            return readRawVarInt64();
        }
    }

    public List<Long> readInt64PackedProperty() {
        return switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> {
                var size = readLengthDelimitedPropertyLength();
                var results = new ArrayList<Long>(size / Long.BYTES);
                try(var input = readRawStream(size)) {
                    while (!input.isFinished()) {
                        results.add(input.readRawVarInt64());
                    }
                }catch (Exception exception) {
                    throw new ProtobufDeserializationException(exception);
                }

                yield results;
            }

            case ProtobufWireType.WIRE_TYPE_VAR_INT -> List.of(readRawVarInt64());
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
    }

    public int readFixed32() {
        if(wireType != ProtobufWireType.WIRE_TYPE_FIXED32) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        } else {
            return readRawFixedInt32();
        }
    }

    public List<Integer> readFixed32PackedProperty() {
        return switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> {
                var size = readLengthDelimitedPropertyLength();
                var results = new ArrayList<Integer>(size / Integer.BYTES);
                try(var input = readRawStream(size)) {
                    while (!input.isFinished()) {
                        results.add(input.readRawFixedInt32());
                    }
                }catch (Exception exception) {
                    throw new ProtobufDeserializationException(exception);
                }

                yield results;
            }

            case ProtobufWireType.WIRE_TYPE_FIXED32 -> List.of(readRawFixedInt32());
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
    }

    public long readFixed64() {
        if(wireType != ProtobufWireType.WIRE_TYPE_FIXED64) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        } else {
            return readRawFixedInt64();
        }
    }

    public List<Long> readFixed64PackedProperty() {
        return switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> {
                var size = readLengthDelimitedPropertyLength();
                var results = new ArrayList<Long>(size / Long.BYTES);
                try(var input = readRawStream(size)) {
                    while (!input.isFinished()) {
                        results.add(input.readRawFixedInt64());
                    }
                }catch (Exception exception) {
                    throw new ProtobufDeserializationException(exception);
                }

                yield results;
            }

            case ProtobufWireType.WIRE_TYPE_FIXED64 -> List.of(readRawFixedInt64());
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
    }

    public void skipUnknownProperty() {
        switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_VAR_INT -> readInt64(); // TODO: Skip me
            case ProtobufWireType.WIRE_TYPE_FIXED32 -> skipRawBytes(Integer.BYTES);
            case ProtobufWireType.WIRE_TYPE_FIXED64 -> skipRawBytes(Long.BYTES);
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> skipRawBytes(readLengthDelimitedPropertyLength());
            case ProtobufWireType.WIRE_TYPE_START_OBJECT -> {
                var index = this.index;
                while (readPropertyTag()) {
                    skipUnknownProperty(); // TODO: Maybe no recursion?
                }
                readEndGroupProperty(index);
            }
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
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
                var value = readRawBytes(size);
                yield new ProtobufUnknownValue.LengthDelimited.Bytes(value);
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

    public abstract boolean isFinished();

    public abstract void skipRawBytes(int size);
    public abstract byte readRawByte();
    public abstract byte[] readRawBytes(int size);
    public abstract ByteBuffer readRawBuffer(int size);
    public abstract ProtobufLazyString readRawLazyString(int size);
    public abstract String readRawDecodedString(int size);
    public abstract int readRawFixedInt32();
    public abstract long readRawFixedInt64();
    public abstract int readRawVarInt32();
    public abstract long readRawVarInt64();
    public abstract ProtobufInputStream readRawStream(int size);

    private static final class Stream extends ProtobufInputStream {
        private static final int MAX_VAR_INT_SIZE = 10;

        private final InputStream inputStream;
        private final boolean autoclose;
        private final long length;
        private long position;

        private final byte[] rewindBuffer;
        private int rewindBufferReadPosition;
        private int rewindBufferWritePosition;
        private int rewindBufferLength;

        private final byte[] scalarBuffer;

        private Stream(InputStream inputStream, boolean autoclose) {
            this.inputStream = inputStream;
            this.autoclose = autoclose;
            this.length = -1;
            this.rewindBuffer = new byte[MAX_VAR_INT_SIZE];
            this.scalarBuffer = new byte[Long.BYTES];
        }

        private Stream(InputStream inputStream, long length, byte[] rewindBuffer, int rewindBufferReadPosition, int rewindBufferWritePosition, int rewindBufferLength, byte[] scalarBuffer) {
            this.inputStream = inputStream;
            this.autoclose = false;
            this.length = length;
            this.rewindBuffer = rewindBuffer;
            this.rewindBufferReadPosition = rewindBufferReadPosition;
            this.rewindBufferWritePosition = rewindBufferWritePosition;
            this.rewindBufferLength = rewindBufferLength;
            this.scalarBuffer = scalarBuffer;
        }

        @Override
        public byte readRawByte() {
            if(length != -1) {
                position++;
            }

            if(rewindBufferLength > 0) {
                rewindBufferLength--;
                return rewindBuffer[rewindBufferReadPosition++];
            }

            var read = readOrThrow();
            if(read == -1) {
                throw ProtobufDeserializationException.truncatedMessage();
            }

            return rewindBuffer[rewindBufferWritePosition++ % rewindBuffer.length] = (byte) read;
        }

        @Override
        public byte[] readRawBytes(int size) {
            return readBytesFromStream(size);
        }

        @Override
        public ByteBuffer readRawBuffer(int size) {
            var bytes = readBytesFromStream(size);
            return ByteBuffer.wrap(bytes);
        }

        @Override
        public ProtobufLazyString readRawLazyString(int size) {
            var bytes = readBytesFromStream(size);
            return ProtobufLazyString.of(bytes, 0, size);
        }

        @Override
        public String readRawDecodedString(int size) {
            var bytes = readBytesFromStream(size);
            return new String(bytes, 0, size);
        }

        @Override
        public boolean isFinished() {
            if(length != -1) {
                return position >= length;
            }else if(rewindBufferLength > 0) {
                return false;
            } else {
                mark();
                var data = readOrThrow();
                if(data == -1) {
                    return true;
                }else {
                    rewind();
                    return false;
                }
            }
        }

        @Override
        public void skipRawBytes(int length) {
            if (this.length != -1) {
                position += length;
            }

            var totalBytesRead = 0L;
            var bytesFromBuffer = Math.min(length, rewindBufferLength);
            if (bytesFromBuffer > 0) {
                totalBytesRead += bytesFromBuffer;
                rewindBufferReadPosition += bytesFromBuffer;
                rewindBufferLength -= bytesFromBuffer;
            }

            while (totalBytesRead < length) {
                var bytesReadFromStream = skipOrThrow(length - totalBytesRead);
                totalBytesRead += bytesReadFromStream;
            }
        }

        private void mark() {
            this.rewindBufferReadPosition = 0;
            this.rewindBufferWritePosition = 0;
        }

        private void rewind() {
            this.rewindBufferReadPosition = 0;
            this.rewindBufferLength = rewindBufferWritePosition - rewindBufferReadPosition;
            if(length != -1) {
                this.position -= rewindBufferLength;
            }
        }

        @Override
        public int readRawFixedInt32() {
            readBytesFromStream(scalarBuffer, Integer.BYTES);
            return (scalarBuffer[0] & 0xFF)
                   | (scalarBuffer[1] & 0xFF) << 8
                   | (scalarBuffer[2] & 0xFF) << 16
                   | (scalarBuffer[3] & 0xFF) << 24;
        }

        @Override
        public long readRawFixedInt64() {
            readBytesFromStream(scalarBuffer, Long.BYTES);
            return  (scalarBuffer[0] & 0xFFL)
                    | (scalarBuffer[1] & 0xFFL) << 8
                    | (scalarBuffer[2] & 0xFFL) << 16
                    | (scalarBuffer[3] & 0xFFL) << 24
                    | (scalarBuffer[4] & 0xFFL) << 32
                    | (scalarBuffer[5] & 0xFFL) << 40
                    | (scalarBuffer[6] & 0xFFL) << 48
                    | (scalarBuffer[7] & 0xFFL) << 56;
        }

        @Override
        public Stream readRawStream(int size) {
            var result = new Stream(inputStream, size, rewindBuffer, rewindBufferReadPosition, rewindBufferWritePosition, rewindBufferLength, scalarBuffer);
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

        private byte[] readBytesFromStream(int size) {
            var result = new byte[size];
            readBytesFromStream(result, size);
            return result;
        }

        private void readBytesFromStream(byte[] output, int length) {
            if (this.length != -1) {
                position += length;
            }

            var totalBytesRead = 0;
            var bytesFromBuffer = Math.min(length, rewindBufferLength);
            if (bytesFromBuffer > 0) {
                System.arraycopy(rewindBuffer, rewindBufferReadPosition, output, 0, bytesFromBuffer);
                totalBytesRead += bytesFromBuffer;
                rewindBufferReadPosition += bytesFromBuffer;
                rewindBufferLength -= bytesFromBuffer;
            }

            while (totalBytesRead < length) {
                var bytesReadFromStream = readOrThrow(output, totalBytesRead, length - totalBytesRead);
                if (bytesReadFromStream == -1) {
                    throw ProtobufDeserializationException.truncatedMessage();
                }

                for (var i = 0; i < bytesReadFromStream; i++) {
                    rewindBuffer[rewindBufferWritePosition % rewindBuffer.length] = output[totalBytesRead + i];
                    rewindBufferWritePosition++;
                }
                totalBytesRead += bytesReadFromStream;
            }
        }

        private int readOrThrow() {
            try {
                return inputStream.read();
            } catch (IOException exception) {
                throw new ProtobufDeserializationException(exception);
            }
        }

        private int readOrThrow(byte[] result, int offset, int length) {
            try {
                return inputStream.read(result, offset, length);
            }catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        private long skipOrThrow(long length) {
            try {
                return inputStream.skip(length);
            }catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        // Source: https://github.com/protocolbuffers/protobuf/blob/main/java/core/src/main/java/com/google/protobuf/CodedInputStream.java
        // Fastest implementation I could find
        // Adapted to work with Channels
        @Override
        public int readRawVarInt32() {
            mark();
            fspath:
            {
                int x;
                if ((x = readRawByte()) >= 0) {
                    return x;
                } else if ((x ^= (readRawByte() << 7)) < 0) {
                    x ^= (~0 << 7);
                } else if ((x ^= (readRawByte() << 14)) >= 0) {
                    x ^= (~0 << 7) ^ (~0 << 14);
                } else if ((x ^= (readRawByte() << 21)) < 0) {
                    x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21);
                } else {
                    int y = readRawByte();
                    x ^= y << 28;
                    x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21) ^ (~0 << 28);
                    if (y < 0
                        && readRawByte() < 0
                        && readRawByte() < 0
                        && readRawByte() < 0
                        && readRawByte() < 0
                        && readRawByte() < 0) {
                        break fspath;
                    }
                }
                return x;
            }

            rewind();
            return (int) readVarIntSlowPath();
        }

        // Source: https://github.com/protocolbuffers/protobuf/blob/main/java/core/src/main/java/com/google/protobuf/CodedInputStream.java
        // Fastest implementation I could find
        // Adapted to work with Channels
        @Override
        public long readRawVarInt64() {
            mark();
            fspath:
            {
                long x;
                int y;
                if ((y = readRawByte()) >= 0) {
                    return y;
                } else if ((y ^= (readRawByte() << 7)) < 0) {
                    x = y ^ (~0 << 7);
                } else if ((y ^= (readRawByte() << 14)) >= 0) {
                    x = y ^ ((~0 << 7) ^ (~0 << 14));
                } else if ((y ^= (readRawByte() << 21)) < 0) {
                    x = y ^ ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
                } else if ((x = y ^ ((long) readRawByte() << 28)) >= 0L) {
                    x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28);
                } else if ((x ^= ((long) readRawByte() << 35)) < 0L) {
                    x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35);
                } else if ((x ^= ((long) readRawByte() << 42)) >= 0L) {
                    x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42);
                } else if ((x ^= ((long) readRawByte() << 49)) < 0L) {
                    x ^=
                            (~0L << 7)
                            ^ (~0L << 14)
                            ^ (~0L << 21)
                            ^ (~0L << 28)
                            ^ (~0L << 35)
                            ^ (~0L << 42)
                            ^ (~0L << 49);
                } else {
                    x ^= ((long) readRawByte() << 56);
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
                        if (readRawByte() < 0L) {
                            break fspath;
                        }
                    }
                }
                return x;
            }

            rewind();
            return readVarIntSlowPath();
        }

        private long readVarIntSlowPath() {
            var result = 0L;
            for (var shift = 0; shift < 64; shift += 7) {
                var b = readRawByte();
                result |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return result;
                }
            }

            throw ProtobufDeserializationException.malformedVarInt();
        }
    }

    private static final class Bytes extends ProtobufInputStream {
        private final byte[] buffer;
        private final int offset;
        private final int length;
        private int position;
  
        private Bytes(byte[] buffer, int offset, int length) {
            this.buffer = buffer;
            this.offset = offset;
            this.length = length;
        }

        @Override
        public byte readRawByte() {
            return buffer[offset + position++];
        }

        @Override
        public byte[] readRawBytes(int size) {
            var result = new byte[size];
            System.arraycopy(buffer, offset, result, 0, size);
            position += size;
            return result;
        }

        @Override
        public ByteBuffer readRawBuffer(int size) {
            var result = ByteBuffer.wrap(buffer, offset + position, size);
            position += size;
            return result;
        }

        @Override
        public ProtobufLazyString readRawLazyString(int size) {
            var result = ProtobufLazyString.of(buffer, offset + position, size);
            position += size;
            return result;
        }

        @Override
        public String readRawDecodedString(int size) {
            var result = new String(buffer, offset + position, size);
            position += size;
            return result;
        }

        @Override
        public boolean isFinished() {
            return position >= length;
        }

        @Override
        public void skipRawBytes(int size) {
            position += size;
        }

        @Override
        public int readRawFixedInt32() {
            var position = offset + this.position;
            this.position += 4;
            return (buffer[position] & 0xFF)
                   | (buffer[position + 1] & 0xFF) << 8
                   | (buffer[position + 2] & 0xFF) << 16
                   | (buffer[position + 3] & 0xFF) << 24;
        }

        @Override
        public long readRawFixedInt64() {
            var position = offset + this.position;
            this.position += 8;
            return  (buffer[position] & 0xFFL)
                    | (buffer[position + 1] & 0xFFL) << 8
                    | (buffer[position + 2] & 0xFFL) << 16
                    |  (buffer[position + 3] & 0xFFL) << 24
                    | (buffer[position + 4] & 0xFFL) << 32
                    | (buffer[position + 5] & 0xFFL) << 40
                    | (buffer[position + 6] & 0xFFL) << 48
                    | (buffer[position + 7] & 0xFFL) << 56;
        }

        @Override
        public Bytes readRawStream(int size) {
            var result = new Bytes(buffer, offset + position, size);
            position += size;
            return result;
        }

        @Override
        public void close() {

        }

        // Source: https://github.com/protocolbuffers/protobuf/blob/main/java/core/src/main/java/com/google/protobuf/CodedInputStream.java
        // Fastest implementation I could find
        // Adapted to work with Channels
        @Override
        public int readRawVarInt32() {
            var position = this.position;
            fspath:
            {
                int x;
                if ((x = buffer[position++]) >= 0) {
                    return x;
                } else if ((x ^= (buffer[position++] << 7)) < 0) {
                    x ^= (~0 << 7);
                } else if ((x ^= (buffer[position++] << 14)) >= 0) {
                    x ^= (~0 << 7) ^ (~0 << 14);
                } else if ((x ^= (buffer[position++] << 21)) < 0) {
                    x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21);
                } else {
                    int y = buffer[position++];
                    x ^= y << 28;
                    x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21) ^ (~0 << 28);
                    if (y < 0
                        && buffer[position++] < 0
                        && buffer[position++] < 0
                        && buffer[position++] < 0
                        && buffer[position++] < 0
                        && buffer[position++] < 0) {
                        break fspath;
                    }
                }
                this.position = position;
                return x;
            }
            
            return (int) readVarIntSlowPath();
        }

        // Source: https://github.com/protocolbuffers/protobuf/blob/main/java/core/src/main/java/com/google/protobuf/CodedInputStream.java
        // Fastest implementation I could find
        // Adapted to work with Channels
        @Override
        public long readRawVarInt64() {
            var position = this.position;
            fspath:
            {
                long x;
                int y;
                if ((y = buffer[position++]) >= 0) {
                    return y;
                } else if ((y ^= (buffer[position++] << 7)) < 0) {
                    x = y ^ (~0 << 7);
                } else if ((y ^= (buffer[position++] << 14)) >= 0) {
                    x = y ^ ((~0 << 7) ^ (~0 << 14));
                } else if ((y ^= (buffer[position++] << 21)) < 0) {
                    x = y ^ ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
                } else if ((x = y ^ ((long) buffer[position++] << 28)) >= 0L) {
                    x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28);
                } else if ((x ^= ((long) buffer[position++] << 35)) < 0L) {
                    x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35);
                } else if ((x ^= ((long) buffer[position++] << 42)) >= 0L) {
                    x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42);
                } else if ((x ^= ((long) buffer[position++] << 49)) < 0L) {
                    x ^=
                            (~0L << 7)
                            ^ (~0L << 14)
                            ^ (~0L << 21)
                            ^ (~0L << 28)
                            ^ (~0L << 35)
                            ^ (~0L << 42)
                            ^ (~0L << 49);
                } else {
                    x ^= ((long) buffer[position++] << 56);
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
                        if (buffer[position++] < 0L) {
                            break fspath;
                        }
                    }
                }
                this.position = position;
                return x;
            }
            
            return readVarIntSlowPath();
        }

        private long readVarIntSlowPath() {
            var result = 0L;
            for (var shift = 0; shift < 64; shift += 7) {
                var b = buffer[position++];
                result |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return result;
                }
            }

            throw ProtobufDeserializationException.malformedVarInt();
        }
    }

    private static final class Buffer extends ProtobufInputStream {
        private static final ThreadLocal<CharsetDecoder> UTF8_DECODER = ThreadLocal.withInitial(StandardCharsets.UTF_8::newDecoder);


        private final ByteBuffer buffer;
        private Buffer(ByteBuffer buffer) {
            this.buffer = buffer.duplicate()
                    .order(ByteOrder.LITTLE_ENDIAN);
        }

        @Override
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
                    var value = readRawBuffer(size);
                    yield new ProtobufUnknownValue.LengthDelimited.Buffer(value); // Use buffer instead of Bytes
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

        @Override
        public byte readRawByte() {
            return buffer.get();
        }

        @Override
        public byte[] readRawBytes(int size) {
            var result = new byte[size];
            buffer.get(result);
            return result;
        }

        @Override
        public ByteBuffer readRawBuffer(int size) {
            var position = buffer.position();
            var result = buffer.slice(position, size);
            buffer.position(position + size);
            return result;
        }

        @Override
        public ProtobufLazyString readRawLazyString(int size) {
            var position = buffer.position();
            var slice = buffer.slice(position, size);
            buffer.position(position + size);
            return ProtobufLazyString.of(slice);
        }

        @Override
        public String readRawDecodedString(int size) {
            try {
                var decoder = UTF8_DECODER.get();
                decoder.reset();
                var position = buffer.position();
                var slice = buffer.slice(position, size);
                var decoded = decoder.decode(slice);
                buffer.position(position + size);
                return decoded.toString();
            }catch (CharacterCodingException _) {
                throw new InternalError();
            }
        }

        @Override
        public boolean isFinished() {
            return !buffer.hasRemaining();
        }

        @Override
        public void skipRawBytes(int size) {
            buffer.position(buffer.position() + size);
        }

        @Override
        public int readRawFixedInt32() {
            return buffer.getInt();
        }

        @Override
        public long readRawFixedInt64() {
           return buffer.getLong();
        }

        @Override
        public Buffer readRawStream(int size) {
            var position = buffer.position();
            var result = new Buffer(buffer.slice(position, size));
            buffer.position(position + size);
            return result;
        }

        @Override
        public void close() {

        }

        // Source: https://github.com/protocolbuffers/protobuf/blob/main/java/core/src/main/java/com/google/protobuf/CodedInputStream.java
        // Fastest implementation I could find
        // Adapted to work with Channels
        @Override
        public int readRawVarInt32() {
            buffer.mark();
            fspath:
            {
                int x;
                if ((x = buffer.get()) >= 0) {
                    return x;
                } else if ((x ^= (buffer.get() << 7)) < 0) {
                    x ^= (~0 << 7);
                } else if ((x ^= (buffer.get() << 14)) >= 0) {
                    x ^= (~0 << 7) ^ (~0 << 14);
                } else if ((x ^= (buffer.get() << 21)) < 0) {
                    x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21);
                } else {
                    int y = buffer.get();
                    x ^= y << 28;
                    x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21) ^ (~0 << 28);
                    if (y < 0
                        && buffer.get() < 0
                        && buffer.get() < 0
                        && buffer.get() < 0
                        && buffer.get() < 0
                        && buffer.get() < 0) {
                        break fspath;
                    }
                }
                return x;
            }

            buffer.reset();
            return (int) readVarIntSlowPath();
        }

        // Source: https://github.com/protocolbuffers/protobuf/blob/main/java/core/src/main/java/com/google/protobuf/CodedInputStream.java
        // Fastest implementation I could find
        // Adapted to work with Channels
        @Override
        public long readRawVarInt64() {
            buffer.mark();
            fspath:
            {
                long x;
                int y;
                if ((y = buffer.get()) >= 0) {
                    return y;
                } else if ((y ^= (buffer.get() << 7)) < 0) {
                    x = y ^ (~0 << 7);
                } else if ((y ^= (buffer.get() << 14)) >= 0) {
                    x = y ^ ((~0 << 7) ^ (~0 << 14));
                } else if ((y ^= (buffer.get() << 21)) < 0) {
                    x = y ^ ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
                } else if ((x = y ^ ((long) buffer.get() << 28)) >= 0L) {
                    x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28);
                } else if ((x ^= ((long) buffer.get() << 35)) < 0L) {
                    x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35);
                } else if ((x ^= ((long) buffer.get() << 42)) >= 0L) {
                    x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42);
                } else if ((x ^= ((long) buffer.get() << 49)) < 0L) {
                    x ^=
                            (~0L << 7)
                            ^ (~0L << 14)
                            ^ (~0L << 21)
                            ^ (~0L << 28)
                            ^ (~0L << 35)
                            ^ (~0L << 42)
                            ^ (~0L << 49);
                } else {
                    x ^= ((long) buffer.get() << 56);
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
                        if (buffer.get() < 0L) {
                            break fspath;
                        }
                    }
                }
                return x;
            }

            buffer.rewind();
            return readVarIntSlowPath();
        }

        private long readVarIntSlowPath() {
            var result = 0L;
            for (var shift = 0; shift < 64; shift += 7) {
                var b = buffer.get();
                result |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return result;
                }
            }

            throw ProtobufDeserializationException.malformedVarInt();
        }
    }
}
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
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
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
    private static final List<Boolean> TRUE_PACKED = List.of(Boolean.TRUE);
    private static final List<Boolean> FALSE_PACKED = List.of(Boolean.FALSE);

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
        return new Bytes(bytes, offset, offset + length);
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

    public SequencedCollection<Float> readFloatPackedProperty() {
        return switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> {
                var size = readLengthDelimitedPropertyLength();
                var results = new ArrayList<Float>(size / Float.BYTES);
                try(var input = readRawStream(size)) {
                    while (!input.isFinished()) {
                        results.add(Float.intBitsToFloat(input.readRawFixedInt32()));
                    }
                }catch (Exception exception) {
                    throw ProtobufDeserializationException.truncatedMessage(exception);
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

    public SequencedCollection<Double> readDoublePackedProperty() {
        return switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> {
                var size = readLengthDelimitedPropertyLength();
                var results = new ArrayList<Double>(size / Double.BYTES);
                try(var input = readRawStream(size)) {
                    while (!input.isFinished()) {
                        results.add(Double.longBitsToDouble(input.readRawFixedInt64()));
                    }
                }catch (Exception exception) {
                    throw ProtobufDeserializationException.truncatedMessage(exception);
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

    public SequencedCollection<Boolean> readBoolPackedProperty(){
        return switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> {
                var size = readLengthDelimitedPropertyLength();
                var results = new ArrayList<Boolean>(size / Integer.BYTES);
                try(var input = readRawStream(size)) {
                    while (!input.isFinished()) {
                        results.add(input.readRawVarInt64() == 1);
                    }
                }catch (Exception exception) {
                    throw ProtobufDeserializationException.truncatedMessage(exception);
                }

                yield results;
            }

            case ProtobufWireType.WIRE_TYPE_VAR_INT -> readRawVarInt64() == 1 ? TRUE_PACKED : FALSE_PACKED;
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

    public SequencedCollection<Integer> readInt32PackedProperty() {
        return switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> {
                var size = readLengthDelimitedPropertyLength();
                var results = new ArrayList<Integer>(size / Integer.BYTES);
                try(var input = readRawStream(size)) {
                    while (!input.isFinished()) {
                        results.add(input.readRawVarInt32());
                    }
                }catch (Exception exception) {
                    throw ProtobufDeserializationException.truncatedMessage(exception);
                }

                yield results;
            }

            case ProtobufWireType.WIRE_TYPE_VAR_INT -> List.of(readRawVarInt32());
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
    }

    public long readInt64Property() {
        if(wireType != ProtobufWireType.WIRE_TYPE_VAR_INT) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        } else {
            return readRawVarInt64();
        }
    }

    public SequencedCollection<Long> readInt64PackedProperty() {
        return switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> {
                var size = readLengthDelimitedPropertyLength();
                var input = readRawStream(size);
                var results = new ArrayList<Long>(input.readInt64PackedPropertyCount());
                try(input) {
                    while (!input.isFinished()) {
                        results.add(input.readRawVarInt64());
                    }
                }catch (Exception exception) {
                    throw ProtobufDeserializationException.truncatedMessage(exception);
                }

                yield results;
            }

            case ProtobufWireType.WIRE_TYPE_VAR_INT -> List.of(readRawVarInt64());
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
    }

    protected abstract int readInt64PackedPropertyCount();

    public int readFixed32() {
        if(wireType != ProtobufWireType.WIRE_TYPE_FIXED32) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        } else {
            return readRawFixedInt32();
        }
    }

    public SequencedCollection<Integer> readFixed32PackedProperty() {
        return switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> {
                var size = readLengthDelimitedPropertyLength();
                var results = new ArrayList<Integer>(size / Integer.BYTES);
                try(var input = readRawStream(size)) {
                    while (!input.isFinished()) {
                        results.add(input.readRawFixedInt32());
                    }
                }catch (Exception exception) {
                    throw ProtobufDeserializationException.truncatedMessage(exception);
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

    public SequencedCollection<Long> readFixed64PackedProperty() {
        return switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> {
                var size = readLengthDelimitedPropertyLength();
                var results = new ArrayList<Long>(size / Long.BYTES);
                try(var input = readRawStream(size)) {
                    while (!input.isFinished()) {
                        results.add(input.readRawFixedInt64());
                    }
                }catch (Exception exception) {
                    throw ProtobufDeserializationException.truncatedMessage(exception);
                }

                yield results;
            }

            case ProtobufWireType.WIRE_TYPE_FIXED64 -> List.of(readRawFixedInt64());
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
    }

    public void skipUnknownProperty() {
        switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_VAR_INT -> skipRawVarInt();
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

    public void skipRawVarInt() {
        for (var shift = 0; shift < 64; shift += 7) {
            if ((readRawByte() & 0x80) == 0) {
                return;
            }
        }

        throw ProtobufDeserializationException.malformedVarInt();
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
        
        private byte nextBufferedValue;
        private boolean hasNextBufferedValue;
        
        private final byte[] scalarBuffer;

        private Stream(InputStream inputStream, boolean autoclose) {
            Objects.requireNonNull(inputStream, "inputStream cannot be null");
            this.inputStream = inputStream;
            this.autoclose = autoclose;
            this.length = -1;
            this.scalarBuffer = new byte[Long.BYTES];
        }

        private Stream(InputStream inputStream, long length, byte[] scalarBuffer) {
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
        public ProtobufLazyString readRawLazyString(int size) {
            var bytes = readRawBytes(size);
            return ProtobufLazyString.of(bytes, 0, size);
        }

        @Override
        public String readRawDecodedString(int size) {
            var bytes = readRawBytes(size);
            return new String(bytes, 0, size, StandardCharsets.UTF_8);
        }

        @Override
        protected int readInt64PackedPropertyCount() {
            if(length == -1) {
                throw new InternalError("Cannot count var ints in a stream with no length");
            } else {
                var result = (int) length;
                if (result != length) {
                    throw new InternalError("Overflow");
                } else {
                    return result;
                }
            }
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
        public Stream readRawStream(int size) {
            var result = new Stream(inputStream, size, scalarBuffer);
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

        // Source: https://github.com/protocolbuffers/protobuf/blob/main/java/core/src/main/java/com/google/protobuf/CodedInputStream.java
        // Fastest implementation I could find
        // Adapted to work with Channels
        @Override
        public int readRawVarInt32() {
            var length = 0;
            fspath:
            {
                int x;
                if ((x = scalarBuffer[length++] = readRawByte()) >= 0) {
                    return x;
                } else if ((x ^= ((scalarBuffer[length++] = readRawByte()) << 7)) < 0) {
                    x ^= (~0 << 7);
                } else if ((x ^= ((scalarBuffer[length++] = readRawByte()) << 14)) >= 0) {
                    x ^= (~0 << 7) ^ (~0 << 14);
                } else if ((x ^= ((scalarBuffer[length++] = readRawByte()) << 21)) < 0) {
                    x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21);
                } else {
                    int y = scalarBuffer[length++] = readRawByte();
                    x ^= y << 28;
                    x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21) ^ (~0 << 28);
                    if (y < 0
                        && (scalarBuffer[length++] = readRawByte()) < 0
                        && (scalarBuffer[length++] = readRawByte()) < 0
                        && (scalarBuffer[length++] = readRawByte()) < 0
                        && (scalarBuffer[length++] = readRawByte()) < 0
                        && (scalarBuffer[length++] = readRawByte()) < 0) {
                        break fspath;
                    }
                }
                return x;
            }
            
            return (int) readVarIntSlowPath(length);
        }

        // Source: https://github.com/protocolbuffers/protobuf/blob/main/java/core/src/main/java/com/google/protobuf/CodedInputStream.java
        // Fastest implementation I could find
        // Adapted to work with Channels
        @Override
        public long readRawVarInt64() {
            var length = 0;
            fspath:
            {
                long x;
                int y;
                if ((y = (scalarBuffer[length++] = readRawByte())) >= 0) {
                    return y;
                } else if ((y ^= ((scalarBuffer[length++] = readRawByte()) << 7)) < 0) {
                    x = y ^ (~0 << 7);
                } else if ((y ^= ((scalarBuffer[length++] = readRawByte()) << 14)) >= 0) {
                    x = y ^ ((~0 << 7) ^ (~0 << 14));
                } else if ((y ^= ((scalarBuffer[length++] = readRawByte()) << 21)) < 0) {
                    x = y ^ ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
                } else if ((x = y ^ ((long) (scalarBuffer[length++] = readRawByte()) << 28)) >= 0L) {
                    x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28);
                } else if ((x ^= ((long) (scalarBuffer[length++] = readRawByte()) << 35)) < 0L) {
                    x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35);
                } else if ((x ^= ((long) (scalarBuffer[length++] = readRawByte()) << 42)) >= 0L) {
                    x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42);
                } else if ((x ^= ((long) (scalarBuffer[length++] = readRawByte()) << 49)) < 0L) {
                    x ^=
                            (~0L << 7)
                            ^ (~0L << 14)
                            ^ (~0L << 21)
                            ^ (~0L << 28)
                            ^ (~0L << 35)
                            ^ (~0L << 42)
                            ^ (~0L << 49);
                } else {
                    x ^= ((long) (scalarBuffer[length++] = readRawByte()) << 56);
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
                        if ((scalarBuffer[length++] = readRawByte()) < 0L) {
                            break fspath;
                        }
                    }
                }
                return x;
            }
            return readVarIntSlowPath(length);
        }

        private long readVarIntSlowPath(int scalarBufferedLength) {
            var result = 0L;
            var shift = 0;
            for (var offset = 0; offset < scalarBufferedLength; offset++, shift += 7) {
                var b = scalarBuffer[offset];
                result |= (long) (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return result;
                }
            }
            for (; shift < 64; shift += 7) {
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
        private final int limit;
        private int offset;

        private Bytes(byte[] buffer, int offset, int limit) {
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
            } catch (IndexOutOfBoundsException _) {
                throw ProtobufDeserializationException.truncatedMessage();
            }
        }

        @Override
        public ProtobufLazyString readRawLazyString(int size) {
            try {
                var result = ProtobufLazyString.of(buffer, offset, size);
                offset += size;
                return result;
            } catch (IndexOutOfBoundsException _) {
                throw ProtobufDeserializationException.truncatedMessage();
            }
        }

        @Override
        public String readRawDecodedString(int size) {
            try {
                var result = new String(buffer, offset, size, StandardCharsets.UTF_8);
                offset += size;
                return result;
            } catch (IndexOutOfBoundsException _) {
                throw ProtobufDeserializationException.truncatedMessage();
            }
        }

        @Override
        protected int readInt64PackedPropertyCount() {
            int offset = this.offset;
            int count = 0;
            int shift;
            while (offset < limit) {
                loop: {
                    for(shift = 0; shift < 64; shift += 7) {
                        if ((buffer[offset++] & 0x80) == 0) {
                            count++;
                            break loop;
                        }
                    }
                    throw ProtobufDeserializationException.malformedVarInt();
                }
            }
            return count;
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
        public Bytes readRawStream(int size) {
            try {
                var result = new Bytes(buffer, offset, offset + size);
                offset += size;
                return result;
            } catch (IndexOutOfBoundsException _) {
                throw ProtobufDeserializationException.truncatedMessage();
            }
        }

        @Override
        public void close() {

        }

        // Source: https://github.com/protocolbuffers/protobuf/blob/main/java/core/src/main/java/com/google/protobuf/CodedInputStream.java
        // Fastest implementation I could find
        // Adapted to work with Channels
        @Override
        public int readRawVarInt32() {
            var offset = this.offset;
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
                    var y = readRawByte();
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

            this.offset = offset;
            return (int) readVarIntSlowPath();
        }

        // Source: https://github.com/protocolbuffers/protobuf/blob/main/java/core/src/main/java/com/google/protobuf/CodedInputStream.java
        // Fastest implementation I could find
        // Adapted to work with Channels
        @Override
        public long readRawVarInt64() {
            var offset = this.offset;
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

            this.offset = offset;
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

    private static final class Buffer extends ProtobufInputStream {
        private static final ThreadLocal<CharsetDecoder> UTF8_DECODER = ThreadLocal.withInitial(() ->
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPLACE)
                        .onUnmappableCharacter(CodingErrorAction.REPLACE));

        private final ByteBuffer buffer;
        private Buffer(ByteBuffer buffer) {
            Objects.requireNonNull(buffer, "buffer cannot be null");
            this.buffer = buffer.duplicate()
                    .order(ByteOrder.LITTLE_ENDIAN);
        }

        @Override
        protected int readInt64PackedPropertyCount() {
            buffer.mark();
            int count = 0;
            int shift;
            while (buffer.hasRemaining()) {
                loop: {
                    for(shift = 0; shift < 64; shift += 7) {
                        if ((buffer.get() & 0x80) == 0) {
                            count++;
                            break loop;
                        }
                    }
                    throw ProtobufDeserializationException.malformedVarInt();
                }
            }
            buffer.reset();
            return count;
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
        public ProtobufLazyString readRawLazyString(int size) {
            try {
                var position = buffer.position();
                var slice = buffer.slice(position, size);
                buffer.position(position + size);
                return ProtobufLazyString.of(slice);
            }catch (IndexOutOfBoundsException _) {
                throw ProtobufDeserializationException.truncatedMessage();
            }
        }

        @Override
        public String readRawDecodedString(int size) {
            try {
                if(buffer.hasArray()) {
                    var result = new String(buffer.array(), buffer.arrayOffset() + buffer.position(), size, StandardCharsets.UTF_8);
                    buffer.position(buffer.position() + size);
                    return result;
                } else {
                    var decoder = UTF8_DECODER.get();
                    decoder.reset();
                    var position = buffer.position();
                    var oldLimit = buffer.limit();
                    buffer.limit(position + size);
                    var decoded = decoder.decode(buffer);
                    buffer.limit(oldLimit);
                    return decoded.toString();
                }
            }catch (IndexOutOfBoundsException _) {
                throw ProtobufDeserializationException.truncatedMessage();
            } catch (CharacterCodingException _) {
                throw new InternalError();
            }
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
        public Buffer readRawStream(int size) {
            try {
                var position = buffer.position();
                var result = new Buffer(buffer.slice(position, size));
                buffer.position(position + size);
                return result;
            } catch (IllegalArgumentException _) {
                throw ProtobufDeserializationException.truncatedMessage();
            }
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

            buffer.reset();
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
}
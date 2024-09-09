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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.InvalidMarkException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ProtobufInputStream {
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    private final Input input;
    private int wireType;
    private int index;
    public ProtobufInputStream(byte[] buffer, int offset, int length) {
        this.input = Input.wrap(buffer, offset, length);
    }
    
    public ProtobufInputStream(Path channel) throws IOException {
        this(Files.newInputStream(channel), Files.size(channel));
    }

    public ProtobufInputStream(InputStream inputStream, long size){
        this.input = Input.wrap(inputStream, size);
    }

    private ProtobufInputStream(Input input) {
        this.input = input;
    }

    public boolean readTag() {
        if(isFinished()) {
            this.wireType = 0;
            return false;
        }

        var rawTag = readInt32Unchecked();
        this.wireType = rawTag & 7;
        this.index = rawTag >>> 3;
        if(index == 0) {
            throw ProtobufDeserializationException.invalidFieldIndex(index);
        }
        return true;
    }

    public List<Float> readFloatPacked() {
        return switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> {
                var results = new ArrayList<Float>();
                var input = lengthDelimitedStream();
                this.wireType = ProtobufWireType.WIRE_TYPE_FIXED32;
                while (!input.isFinished()){
                    results.add(input.readFloat());
                }

                yield results;
            }

            case ProtobufWireType.WIRE_TYPE_FIXED32 -> List.of(readFloat());
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
    }

    public List<Double> readDoublePacked() {
        return switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> {
                var results = new ArrayList<Double>();
                var input = lengthDelimitedStream();
                this.wireType = ProtobufWireType.WIRE_TYPE_FIXED64;
                while (!input.isFinished()){
                    results.add(input.readDouble());
                }

                yield results;
            }

            case ProtobufWireType.WIRE_TYPE_FIXED64 -> List.of(readDouble());
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
    }

    public List<Integer> readInt32Packed() {
        return switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> {
                var results = new ArrayList<Integer>();
                var input = lengthDelimitedStream();
                this.wireType = ProtobufWireType.WIRE_TYPE_VAR_INT;
                while (!input.isFinished()){
                    results.add(input.readInt32());
                }

                yield results;
            }

            case ProtobufWireType.WIRE_TYPE_VAR_INT -> List.of(readInt32());
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
    }

    public List<Long> readInt64Packed() {
        return switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> {
                var results = new ArrayList<Long>();
                var input = lengthDelimitedStream();
                this.wireType = ProtobufWireType.WIRE_TYPE_VAR_INT;
                while (!input.isFinished()){
                    results.add(input.readInt64());
                }

                yield results;
            }

            case ProtobufWireType.WIRE_TYPE_VAR_INT -> List.of(readInt64());
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
    }

    public List<Integer> readFixed32Packed() {
        return switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> {
                var results = new ArrayList<Integer>();
                var input = lengthDelimitedStream();
                this.wireType = ProtobufWireType.WIRE_TYPE_FIXED32;
                while (!input.isFinished()){
                    results.add(input.readFixed32());
                }

                yield results;
            }

            case ProtobufWireType.WIRE_TYPE_FIXED32 -> List.of(readFixed32());
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
    }

    public List<Long> readFixed64Packed() {
        return switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> {
                var results = new ArrayList<Long>();
                var input = lengthDelimitedStream();
                this.wireType = ProtobufWireType.WIRE_TYPE_FIXED64;
                while (!input.isFinished()){
                    results.add(input.readFixed64());
                }

                yield results;
            }

            case ProtobufWireType.WIRE_TYPE_FIXED64 -> List.of(readFixed64());
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
    }

    public List<Boolean> readBoolPacked(){
        return switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> {
                var results = new ArrayList<Boolean>();
                var input = lengthDelimitedStream();
                this.wireType = ProtobufWireType.WIRE_TYPE_VAR_INT;
                while (!input.isFinished()){
                    results.add(input.readBool());
                }

                yield results;
            }

            case ProtobufWireType.WIRE_TYPE_VAR_INT -> List.of(readBool());
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
    }

    public float readFloat() {
        return Float.intBitsToFloat(readFixed32());
    }

    public double readDouble() {
        return Double.longBitsToDouble(readFixed64());
    }

    public boolean readBool(){
        return readInt64() == 1;
    }

    public ProtobufString readString() {
        if(wireType != ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        }

        var size = this.readInt32Unchecked();
        if(size < 0) {
            throw ProtobufDeserializationException.negativeLength(size);
        }else {
            return input.readString(size);
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
        input.mark();
        fspath:
        {
            int x;
            if ((x = input.readByte()) >= 0) {
                return x;
            } else if ((x ^= (input.readByte() << 7)) < 0) {
                x ^= (~0 << 7);
            } else if ((x ^= (input.readByte() << 14)) >= 0) {
                x ^= (~0 << 7) ^ (~0 << 14);
            } else if ((x ^= (input.readByte() << 21)) < 0) {
                x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21);
            } else {
                int y = input.readByte();
                x ^= y << 28;
                x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21) ^ (~0 << 28);
                if (y < 0
                        && input.readByte() < 0
                        && input.readByte() < 0
                        && input.readByte() < 0
                        && input.readByte() < 0
                        && input.readByte() < 0) {
                    break fspath;
                }
            }
            return x;
        }

        input.rewind();
        return (int) readVarInt64Slow();
    }

    // Source: https://github.com/protocolbuffers/protobuf/blob/main/java/core/src/main/java/com/google/protobuf/CodedInputStream.java
    // Fastest implementation I could find
    // Adapted to work with Channels
    public long readInt64() {
        if(wireType != ProtobufWireType.WIRE_TYPE_VAR_INT) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        }

        input.mark();
        fspath:
        {
            long x;
            int y;
            if ((y = input.readByte()) >= 0) {
                return y;
            } else if ((y ^= (input.readByte() << 7)) < 0) {
                x = y ^ (~0 << 7);
            } else if ((y ^= (input.readByte() << 14)) >= 0) {
                x = y ^ ((~0 << 7) ^ (~0 << 14));
            } else if ((y ^= (input.readByte() << 21)) < 0) {
                x = y ^ ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
            } else if ((x = y ^ ((long) input.readByte() << 28)) >= 0L) {
                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28);
            } else if ((x ^= ((long) input.readByte() << 35)) < 0L) {
                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35);
            } else if ((x ^= ((long) input.readByte() << 42)) >= 0L) {
                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42);
            } else if ((x ^= ((long) input.readByte() << 49)) < 0L) {
                x ^=
                        (~0L << 7)
                                ^ (~0L << 14)
                                ^ (~0L << 21)
                                ^ (~0L << 28)
                                ^ (~0L << 35)
                                ^ (~0L << 42)
                                ^ (~0L << 49);
            } else {
                x ^= ((long) input.readByte() << 56);
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
                    if (input.readByte() < 0L) {
                        break fspath;
                    }
                }
            }
            return x;
        }

        input.rewind();
        return readVarInt64Slow();
    }

    private long readVarInt64Slow() {
        var result = 0L;
        for (int shift = 0; shift < 64; shift += 7) {
            byte b = input.readByte();
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
        
        return input.readByte() & 255
                | (input.readByte() & 255) << 8
                | (input.readByte() & 255) << 16
                | (input.readByte() & 255) << 24;
    }
    
    public long readFixed64() {
        if(wireType != ProtobufWireType.WIRE_TYPE_FIXED64) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        }
        
        return (long) input.readByte() & 255L
                | ((long) input.readByte() & 255L) << 8
                | ((long) input.readByte() & 255L) << 16
                | ((long) input.readByte() & 255L) << 24
                | ((long) input.readByte() & 255L) << 32
                | ((long) input.readByte() & 255L) << 40
                | ((long) input.readByte() & 255L) << 48
                | ((long) input.readByte() & 255L) << 56;
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
            return input.readBytes(size);
        }
    }

    public Object readUnknown() {
        return readUnknown(true);
    }

    public Object readUnknown(boolean allocate) {
        return switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_VAR_INT -> readInt64();
            case ProtobufWireType.WIRE_TYPE_FIXED32 -> readFixed32();
            case ProtobufWireType.WIRE_TYPE_FIXED64 -> readFixed64();
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> readBytes();
            case ProtobufWireType.WIRE_TYPE_START_OBJECT -> readGroup(allocate);
            default -> throw ProtobufDeserializationException.invalidWireType(wireType);
        };
    }

    private HashMap<Integer, Object> readGroup(boolean allocate) {
        var group = allocate ? new HashMap<Integer, Object>() : null;
        var groupIndex = index;
        while (!isFinished()) {
            var value = readUnknown();
            if(group != null) {
                group.put(index, value);
            }
        }
        assertGroupClosed(groupIndex);
        return group;
    }

    private void assertGroupClosed(int groupIndex) {
        if(wireType != ProtobufWireType.WIRE_TYPE_END_OBJECT) {
            throw ProtobufDeserializationException.malformedGroup();
        }
        if(index != groupIndex) {
            throw ProtobufDeserializationException.invalidEndObject(index, groupIndex);
        }
    }
    
    public int index() {
        return index;
    }

    public boolean isFinished() {
        return input.isFinished();
    }

    public ProtobufInputStream lengthDelimitedStream() {
        if(wireType != ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        }

        var size = this.readInt32Unchecked();
        if(size < 0) {
            throw ProtobufDeserializationException.negativeLength(size);
        }else {
            return new ProtobufInputStream(input.subInput(size));
        }
    }

    private static abstract sealed class Input {
        long marker;
        private Input() {
            this.marker = -1;
        }

        public abstract byte readByte();
        public abstract ByteBuffer readBytes(int size);
        public abstract ProtobufString readString(int size);
        public abstract void mark();
        public abstract void rewind();
        public abstract boolean isFinished();
        public abstract Input subInput(int size);

        private static Input wrap(InputStream channel, long size) {
            return new Stream(channel, size);
        }

        private static Input wrap(byte[] buffer, int offset, int size) {
            return new Buffer(buffer, offset, size);
        }
        
        private static final class Stream extends Input{
            private final InputStream inputStream;
            private long remaining;

            // A buffer is needed to rewind if decoding a varint fails
            // So the max size this can use is 10 bytes
            private byte[] buffer;
            private int bufferPosition;
            private int bufferLength;

            private Stream(InputStream inputStream, long size) {
                this.inputStream = inputStream;
                this.remaining = size;
            }

            @Override
            public byte readByte() {
                try {
                    if(marker == -1 && bufferLength != 0) {
                        bufferLength--;
                        remaining--;
                        return buffer[bufferPosition++];
                    }

                    if(remaining-- <= 0) {
                        throw new EOFException();
                    }

                    return (byte) inputStream.read();
                }catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            }

            @Override
            public ByteBuffer readBytes(int size) {
                try {
                    if((this.remaining = remaining - size) < 0) {
                        throw new EOFException();
                    }

                    var bytes = new byte[size];
                    var offset = 0;
                    if(bufferLength != 0) {
                        System.arraycopy(buffer, 0, bytes, 0, bufferLength);
                        offset = bufferLength;
                        bufferLength = Math.max(bufferLength - size, 0);
                    }

                    inputStream.readNBytes(bytes, offset, size - offset);
                    return ByteBuffer.wrap(bytes);
                }catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            }

            @Override
            public ProtobufString readString(int size) {
                try {
                    if((this.remaining = remaining - size) < 0) {
                        throw new EOFException();
                    }

                    var bytes = new byte[size];
                    var offset = 0;
                    if(bufferLength != 0) {
                        System.arraycopy(buffer, 0, bytes, 0, bufferLength);
                        offset = bufferLength;
                        bufferLength = Math.max(bufferLength - size, 0);
                    }

                    inputStream.readNBytes(bytes, offset, size - offset);
                    return ProtobufString.lazy(bytes, 0, size);
                }catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            }

            @Override
            public void mark() {
                if(this.buffer != null) {
                    this.buffer = new byte[10];
                }

                this.bufferLength = 0;
                this.marker = remaining;
            }

            @Override
            public void rewind() {
                this.marker = -1;
                this.bufferPosition = 0;
                this.remaining += bufferLength;
            }

            @Override
            public boolean isFinished() {
                return remaining <= 0;
            }

            @Override
            public Input subInput(int size) {
                var result = new Stream(inputStream, size);
                remaining -= size;
                return result;
            }
        }

        private static final class Buffer extends Input{
            private final byte[] buffer;
            private final int offset;
            private final int length;
            private int position;
            private int marker;
            private Buffer(byte[] buffer, int offset, int length) {
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
            public ProtobufString readString(int size) {
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
            public Input subInput(int size) {
                var result = new Buffer(buffer, offset + position, size);
                position += size;
                return result;
            }
        }
    }
}
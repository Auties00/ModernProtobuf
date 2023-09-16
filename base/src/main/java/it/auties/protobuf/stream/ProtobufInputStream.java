package it.auties.protobuf.stream;

import it.auties.protobuf.exception.ProtobufDeserializationException;
import it.auties.protobuf.model.ProtobufWireType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProtobufInputStream {
    private final byte[] buffer;
    private final int limit;
    private int wireType;
    private int index;
    private int pos;

    public ProtobufInputStream(byte[] buffer) {
        this.buffer = buffer;
        this.limit = buffer.length;
        this.pos = 0;
    }

    public boolean readTag() {
        if(isAtEnd()) {
            this.wireType = 0;
            return false;
        }

        var rawTag = readInt32Unchecked();
        this.wireType = rawTag & 7;
        this.index = rawTag >>> 3;
        return true;
    }

    public List<Float> readFloatPacked() {
        return switch (wireType) {
            case ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED -> {
                var results = new ArrayList<Float>();
                var input = new ProtobufInputStream(readBytes());
                this.wireType = ProtobufWireType.WIRE_TYPE_FIXED32;
                while (!input.isAtEnd()){
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
                var input = new ProtobufInputStream(readBytes());
                this.wireType = ProtobufWireType.WIRE_TYPE_FIXED64;
                while (!input.isAtEnd()){
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
                var input = new ProtobufInputStream(readBytes());
                this.wireType = ProtobufWireType.WIRE_TYPE_VAR_INT;
                while (!input.isAtEnd()){
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
                var input = new ProtobufInputStream(readBytes());
                this.wireType = ProtobufWireType.WIRE_TYPE_VAR_INT;
                while (!input.isAtEnd()){
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
                var input = new ProtobufInputStream(readBytes());
                this.wireType = ProtobufWireType.WIRE_TYPE_FIXED32;
                while (!input.isAtEnd()){
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
                var input = new ProtobufInputStream(readBytes());
                this.wireType = ProtobufWireType.WIRE_TYPE_FIXED64;
                while (!input.isAtEnd()){
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
                var input = new ProtobufInputStream(readBytes());
                this.wireType = ProtobufWireType.WIRE_TYPE_VAR_INT;
                while (!input.isAtEnd()){
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

    public String readString() {
        return new String(readBytes(), StandardCharsets.UTF_8);
    }

    public int readInt32() {
        if(wireType != ProtobufWireType.WIRE_TYPE_VAR_INT) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        }

        return readInt32Unchecked();
    }

    private int readInt32Unchecked() {
        fspath:
        {
            int tempPos = pos;
            byte[] buffer = this.buffer;
            int x;
            if ((x = buffer[tempPos++]) >= 0) {
                pos = tempPos;
                return x;
            } else if (limit - tempPos < 9) {
                break fspath;
            } else if ((x ^= (buffer[tempPos++] << 7)) < 0) {
                x ^= (~0 << 7);
            } else if ((x ^= (buffer[tempPos++] << 14)) >= 0) {
                x ^= (~0 << 7) ^ (~0 << 14);
            } else if ((x ^= (buffer[tempPos++] << 21)) < 0) {
                x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21);
            } else {
                int y = buffer[tempPos++];
                x ^= y << 28;
                x ^= (~0 << 7) ^ (~0 << 14) ^ (~0 << 21) ^ (~0 << 28);
                if (y < 0
                        && buffer[tempPos++] < 0
                        && buffer[tempPos++] < 0
                        && buffer[tempPos++] < 0
                        && buffer[tempPos++] < 0
                        && buffer[tempPos++] < 0) {
                    break fspath;
                }
            }
            pos = tempPos;
            return x;
        }

        return (int) readVarInt64Slow();
    }
    
    public long readInt64() {
        if(wireType != ProtobufWireType.WIRE_TYPE_VAR_INT) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        }
        
        fspath:
        {
            int tempPos = pos;
            byte[] buffer = this.buffer;
            long x;
            int y;
            if ((y = buffer[tempPos++]) >= 0) {
                pos = tempPos;
                return y;
            } else if (limit - tempPos < 9) {
                break fspath;
            } else if ((y ^= (buffer[tempPos++] << 7)) < 0) {
                x = y ^ (~0 << 7);
            } else if ((y ^= (buffer[tempPos++] << 14)) >= 0) {
                x = y ^ ((~0 << 7) ^ (~0 << 14));
            } else if ((y ^= (buffer[tempPos++] << 21)) < 0) {
                x = y ^ ((~0 << 7) ^ (~0 << 14) ^ (~0 << 21));
            } else if ((x = y ^ ((long) buffer[tempPos++] << 28)) >= 0L) {
                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28);
            } else if ((x ^= ((long) buffer[tempPos++] << 35)) < 0L) {
                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35);
            } else if ((x ^= ((long) buffer[tempPos++] << 42)) >= 0L) {
                x ^= (~0L << 7) ^ (~0L << 14) ^ (~0L << 21) ^ (~0L << 28) ^ (~0L << 35) ^ (~0L << 42);
            } else if ((x ^= ((long) buffer[tempPos++] << 49)) < 0L) {
                x ^=
                        (~0L << 7)
                                ^ (~0L << 14)
                                ^ (~0L << 21)
                                ^ (~0L << 28)
                                ^ (~0L << 35)
                                ^ (~0L << 42)
                                ^ (~0L << 49);
            } else {
                x ^= ((long) buffer[tempPos++] << 56);
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
                    if (buffer[tempPos++] < 0L) {
                        break fspath;
                    }
                }
            }
            pos = tempPos;
            return x;
        }

        return readVarInt64Slow();
    }

    private long readVarInt64Slow() {
        var result = 0L;
        for (int shift = 0; shift < 64; shift += 7) {
            byte b = readByte();
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
        
        var tempPos = this.pos;
        if (this.limit - tempPos < 4) {
            throw ProtobufDeserializationException.truncatedMessage();
        }

        byte[] buffer = this.buffer;
        this.pos = tempPos + 4;
        return buffer[tempPos] & 255 | (buffer[tempPos + 1] & 255) << 8 | (buffer[tempPos + 2] & 255) << 16 | (buffer[tempPos + 3] & 255) << 24;
    }
    
    public long readFixed64() {
        if(wireType != ProtobufWireType.WIRE_TYPE_FIXED64) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        }
        
        int tempPos = this.pos;
        if (this.limit - tempPos < 8) {
            throw ProtobufDeserializationException.truncatedMessage();
        }

        byte[] buffer = this.buffer;
        this.pos = tempPos + 8;
        return (long) buffer[tempPos] & 255L | ((long) buffer[tempPos + 1] & 255L) << 8 | ((long) buffer[tempPos + 2] & 255L) << 16 | ((long) buffer[tempPos + 3] & 255L) << 24 | ((long) buffer[tempPos + 4] & 255L) << 32 | ((long) buffer[tempPos + 5] & 255L) << 40 | ((long) buffer[tempPos + 6] & 255L) << 48 | ((long) buffer[tempPos + 7] & 255L) << 56;
    }

    public byte readByte() {
        return buffer[pos++];
    }

    public byte[] readBytes() {
        if(wireType != ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED) {
            throw ProtobufDeserializationException.invalidWireType(wireType);
        }

        var size = this.readInt32Unchecked();
        if (size > 0 && size <= this.limit - this.pos) {
            this.pos += size;
            return Arrays.copyOfRange(buffer, pos - size, pos);
        }

        return size == 0 ? new byte[0] : this.readBytes(size);
    }

    public void skipBytes() {
        if(isAtEnd()) {
            return;
        }

        var size = this.readInt32Unchecked();
        this.pos += size;
    }

    private byte[] readBytes(int length) {
        var tempPos = pos;
        pos += length;
        return Arrays.copyOfRange(buffer, tempPos, pos);
    }

    public boolean isAtEnd() {
        return pos >= limit;
    }

    public int index() {
        return index;
    }
}
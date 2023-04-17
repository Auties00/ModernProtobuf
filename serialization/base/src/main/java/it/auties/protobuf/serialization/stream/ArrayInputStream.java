package it.auties.protobuf.serialization.stream;

import it.auties.protobuf.serialization.exception.ProtobufDeserializationException;

import java.util.Arrays;

public class ArrayInputStream {
    private final byte[] buffer;
    private final int limit;
    private int pos;

    public ArrayInputStream(byte[] buffer) {
        this.buffer = buffer;
        this.limit = buffer.length;
        this.pos = 0;
    }

    public int readTag() {
        return isAtEnd() ? 0 : readInt32();
    }

    public int readInt32() {
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
        var tempPos = this.pos;
        if (this.limit - tempPos < 4) {
            throw ProtobufDeserializationException.truncatedMessage();
        }

        byte[] buffer = this.buffer;
        this.pos = tempPos + 4;
        return buffer[tempPos] & 255 | (buffer[tempPos + 1] & 255) << 8 | (buffer[tempPos + 2] & 255) << 16 | (buffer[tempPos + 3] & 255) << 24;
    }

    public long readFixed64() {
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
        var size = this.readInt32();
        if (size > 0 && size <= this.limit - this.pos) {
            this.pos += size;
            return Arrays.copyOfRange(buffer, pos - size, pos);
        }

        return size == 0 ? new byte[0] : this.readBytes(size);
    }

    private byte[] readBytes(int length) {
        var tempPos = pos;
        pos += length;
        return Arrays.copyOfRange(buffer, tempPos, pos);
    }

    public boolean isAtEnd() {
        return pos >= limit;
    }

    public int position() {
        return pos;
    }
}
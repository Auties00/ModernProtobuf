package it.auties.protobuf.decoder;

import java.io.IOException;
import java.util.Arrays;

public class ArrayInputStream {
    private final byte[] buffer;
    private final int limit;
    private int pos;
    private int lastTag;

    public ArrayInputStream(byte[] buffer) {
        this.buffer = buffer;
        this.limit = buffer.length;
        this.pos = 0;
    }

    public int readTag() throws IOException {
        if (isAtEnd()) {
            this.lastTag = 0;
            return 0;
        }

        this.lastTag = readRawVarint32();
        if (getTagFieldNumber(lastTag) == 0) {
            throw InvalidProtocolBufferException.invalidTag();
        }

        return lastTag;
    }

    private int getTagFieldNumber(int tag) {
        return tag >>> 3;
    }

    public int readRawVarint32() throws IOException {
        fspath:
        {
            int tempPos = pos;

            if (limit == tempPos) {
                break fspath;
            }

            final byte[] buffer = this.buffer;
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

        return (int) readRawVarint64SlowPath();
    }

    long readRawVarint64SlowPath() throws IOException {
        long result = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            final byte b = readRawByte();
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
        }

        throw InvalidProtocolBufferException.malformedVarint();
    }

    public byte readRawByte() throws IOException {
        if (pos == limit) {
            throw InvalidProtocolBufferException.truncatedMessage();
        }

        return buffer[pos++];
    }

    public void checkLastTagWas(final int value) throws InvalidProtocolBufferException {
        if (lastTag == value) {
            return;
        }

        throw InvalidProtocolBufferException.invalidEndTag(lastTag);
    }

    public long readInt64() throws IOException {
        return this.readRawVarint64();
    }

    public long readRawVarint64() throws IOException {
        fspath:
        {
            int tempPos = pos;

            if (limit == tempPos) {
                break fspath;
            }

            final byte[] buffer = this.buffer;
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

        return readRawVarint64SlowPath();
    }


    public long readFixed64() throws IOException {
        return this.readRawLittleEndian64();
    }

    public long readRawLittleEndian64() throws IOException {
        int tempPos = this.pos;
        if (this.limit - tempPos < 8) {
            throw InvalidProtocolBufferException.truncatedMessage();
        }

        byte[] buffer = this.buffer;
        this.pos = tempPos + 8;
        return (long) buffer[tempPos] & 255L | ((long) buffer[tempPos + 1] & 255L) << 8 | ((long) buffer[tempPos + 2] & 255L) << 16 | ((long) buffer[tempPos + 3] & 255L) << 24 | ((long) buffer[tempPos + 4] & 255L) << 32 | ((long) buffer[tempPos + 5] & 255L) << 40 | ((long) buffer[tempPos + 6] & 255L) << 48 | ((long) buffer[tempPos + 7] & 255L) << 56;
    }

    public byte[] readBytes() throws IOException {
        int size = this.readRawVarint32();
        if (size > 0 && size <= this.limit - this.pos) {
            var result = new byte[size];
            System.arraycopy(buffer, pos, result, 0, size);
            this.pos += size;
            return result;
        }

        return size == 0 ? new byte[0]: this.readRawBytes(size);
    }

    public byte[] readRawBytes(final int length) throws IOException {
        if (length > 0 && length <= (limit - pos)) {
            final int tempPos = pos;
            pos += length;
            return Arrays.copyOfRange(buffer, tempPos, pos);
        }

        if (length <= 0) {
            if (length == 0) {
                return new byte[0];
            }

            throw InvalidProtocolBufferException.negativeSize();
        }

        throw InvalidProtocolBufferException.truncatedMessage();
    }


    public int readFixed32() throws IOException {
        return this.readRawLittleEndian32();
    }

    public int readRawLittleEndian32() throws IOException {
        int tempPos = this.pos;
        if (this.limit - tempPos < 4) {
            throw InvalidProtocolBufferException.truncatedMessage();
        }

        byte[] buffer = this.buffer;
        this.pos = tempPos + 4;
        return buffer[tempPos] & 255 | (buffer[tempPos + 1] & 255) << 8 | (buffer[tempPos + 2] & 255) << 16 | (buffer[tempPos + 3] & 255) << 24;
    }

    public boolean isAtEnd() {
        return this.pos == this.limit;
    }
}

package it.auties.protobuf.encoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

public record ArrayOutputStream(ByteArrayOutputStream buffer) {
    public final void writeTag(int fieldNumber, int wireType) {
        this.writeUInt32NoTag(makeTag(fieldNumber, wireType));
    }

    private int makeTag(int fieldNumber, int wireType) {
        return fieldNumber << 3 | wireType;
    }

    public final void writeInt32(int fieldNumber, int value) {
        this.writeTag(fieldNumber, 0);
        this.writeInt32NoTag(value);
    }

    public final void writeUInt32(int fieldNumber, int value) {
        this.writeTag(fieldNumber, 0);
        this.writeUInt32NoTag(value);
    }

    public final void writeFixed32(int fieldNumber, int value) {
        this.writeTag(fieldNumber, 5);
        this.writeFixed32NoTag(value);
    }

    public final void writeUInt64(int fieldNumber, long value) {
        this.writeTag(fieldNumber, 0);
        this.writeUInt64NoTag(value);
    }

    public final void writeFixed64(int fieldNumber, long value) {
        this.writeTag(fieldNumber, 1);
        this.writeFixed64NoTag(value);
    }

    public final void writeBool(int fieldNumber, boolean value) {
        this.writeTag(fieldNumber, 0);
        this.write((byte)(value ? 1 : 0));
    }

    public final void writeBytes(int fieldNumber, byte[] value) throws IOException {
        this.writeTag(fieldNumber, 2);
        this.writeBytesNoTag(value);
    }

    public final void writeByteArray(int fieldNumber, byte[] value) throws IOException {
        this.writeByteArray(fieldNumber, value, 0, value.length);
    }

    public final void writeByteArray(int fieldNumber, byte[] value, int offset, int length) throws IOException {
        this.writeTag(fieldNumber, 2);
        this.writeByteArrayNoTag(value, offset, length);
    }

    public final void writeBytesNoTag(byte[] value) throws IOException {
        this.writeUInt32NoTag(value.length);
        writeLazy(value, 0, value.length);
    }

    public final void writeByteArrayNoTag(byte[] value, int offset, int length) throws IOException {
        this.writeUInt32NoTag(length);
        this.write(value, offset, length);
    }

    public final void write(byte value) {
        buffer.write(value);
    }

    public final void writeInt32NoTag(int value) {
        if (value >= 0) {
            this.writeUInt32NoTag(value);
        } else {
            this.writeUInt64NoTag(value);
        }
    }

    public final void writeUInt32NoTag(int value) {
        while((value & -128) != 0) {
            write((byte) (value & 127 | 128));
            value >>>= 7;
        }

        write((byte)value);
    }

    public final void writeFixed32NoTag(int value) {
        write((byte)(value & 255));
        write((byte)(value >> 8 & 255));
        write((byte)(value >> 16 & 255));
        write((byte)(value >> 24 & 255));
    }

    public final void writeUInt64NoTag(long value) {
        while((value & -128L) != 0L) {
            write((byte)((int)value & 127 | 128));
            value >>>= 7;
        }

        write((byte)((int)value));
    }

    public final void writeFixed64NoTag(long value) {
        write((byte)((int)value & 255));
        write((byte)((int)(value >> 8) & 255));
        write((byte)((int)(value >> 16) & 255));
        write((byte)((int)(value >> 24) & 255));
        write((byte)((int)(value >> 32) & 255));
        write((byte)((int)(value >> 40) & 255));
        write((byte)((int)(value >> 48) & 255));
        write((byte)((int)(value >> 56) & 255));
    }

    public final void write(byte[] value, int offset, int length) throws IOException {
        buffer.write(Arrays.copyOfRange(value, offset, offset + length));
    }

    public final void writeLazy(byte[] value, int offset, int length) throws IOException {
        this.write(value, offset, length);
    }
}

package it.auties.protobuf.encoder;

import lombok.SneakyThrows;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public record ArrayOutputStream(ByteArrayOutputStream buffer) {
    public static final int VAR_INT = 0;
    public static final int FIXED_32 = 5;
    public static final int FIXED_64 = 1;
    public static final int DELIMITED = 2;

    public ArrayOutputStream() {
        this(new ByteArrayOutputStream());
    }

    public void writeTag(int fieldNumber, int wireType) {
        writeUInt32NoTag((fieldNumber << 3) | wireType);
    }

    public void writeUInt32(int fieldNumber, int value) {
        writeTag(fieldNumber, VAR_INT);
        writeUInt32NoTag(value);
    }

    public void writeFixed32(int fieldNumber, int value) {
        writeTag(fieldNumber, FIXED_32);
        writeFixed32NoTag(value);
    }

    public void writeUInt64(int fieldNumber, long value) {
        writeTag(fieldNumber, VAR_INT);
        writeUInt64NoTag(value);
    }

    public void writeFixed64(int fieldNumber, long value) {
        writeTag(fieldNumber, FIXED_64);
        writeFixed64NoTag(value);
    }

    public void writeBool(int fieldNumber, boolean value) {
        writeTag(fieldNumber, VAR_INT);
        write((byte) (value ? 1 : 0));
    }

    public void writeString(int fieldNumber, String value) {
        writeTag(fieldNumber, DELIMITED);
        writeStringNoTag(value);
    }

    public void writeBytes(int fieldNumber, byte[] value) {
        writeTag(fieldNumber, DELIMITED);
        writeBytesNoTag(value);
    }

    @SneakyThrows
    public void writeBytesNoTag(byte[] value) {
        writeUInt32NoTag(value.length);
        buffer.write(value);
    }

    public void write(byte value) {
        buffer.write(value);
    }

    public void writeUInt32NoTag(int value) {
        while (true) {
            if ((value & ~0x7F) == 0) {
                write((byte) value);
                return;
            }

            write((byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
    }

    public void writeFixed32NoTag(int value) {
        write((byte) (value & 0xFF));
        write((byte) ((value >> 8) & 0xFF));
        write((byte) ((value >> 16) & 0xFF));
        write((byte) ((value >> 24) & 0xFF));
    }

    public void writeUInt64NoTag(long value) {
        while (true) {
            if ((value & ~0x7FL) == 0) {
                write((byte) value);
                return;
            }

            write((byte) (((int) value & 0x7F) | 0x80));
            value >>>= 7;
        }
    }

    public void writeFixed64NoTag(long value) {
        write((byte) ((int) (value) & 0xFF));
        write((byte) ((int) (value >> 8) & 0xFF));
        write((byte) ((int) (value >> 16) & 0xFF));
        write((byte) ((int) (value >> 24) & 0xFF));
        write((byte) ((int) (value >> 32) & 0xFF));
        write((byte) ((int) (value >> 40) & 0xFF));
        write((byte) ((int) (value >> 48) & 0xFF));
        write((byte) ((int) (value >> 56) & 0xFF));
    }

    public void writeStringNoTag(String value) {
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        writeBytesNoTag(bytes);
    }

    public byte[] readResult() {
        return buffer.toByteArray();
    }
}
package it.auties.protobuf.api.util;

import lombok.SneakyThrows;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static it.auties.protobuf.api.util.WireType.*;

public record ArrayOutputStream(ByteArrayOutputStream buffer) {
    static int makeTag(int fieldNumber, int wireType) {
        return (fieldNumber << TAG_TYPE_BITS) | wireType;
    }
    
    public ArrayOutputStream(){
        this(new ByteArrayOutputStream());
    }

    public void writeTag(int fieldNumber, int wireType) {
        writeUInt32NoTag(makeTag(fieldNumber, wireType));
    }

    public void writeInt32(int fieldNumber, int value) {
        writeTag(fieldNumber, WIRE_TYPE_VAR_INT);
        writeInt32NoTag(value);
    }

    public void writeUInt32(int fieldNumber, int value) {
        writeTag(fieldNumber, WIRE_TYPE_VAR_INT);
        writeUInt32NoTag(value);
    }

    public void writeFixed32(int fieldNumber, int value) {
        writeTag(fieldNumber, WIRE_TYPE_FIXED32);
        writeFixed32NoTag(value);
    }

    public void writeInt64(int fieldNumber, long value) {
        writeUInt64(fieldNumber, value);
    }

    public void writeUInt64(int fieldNumber, long value) {
        writeTag(fieldNumber, WIRE_TYPE_VAR_INT);
        writeUInt64NoTag(value);
    }

    public void writeFixed64(int fieldNumber, long value) {
        writeTag(fieldNumber, WIRE_TYPE_FIXED64);
        writeFixed64NoTag(value);
    }

    public void writeBool(int fieldNumber, boolean value) {
        writeTag(fieldNumber, WIRE_TYPE_VAR_INT);
        writeRaw((byte) (value ? 1 : 0));
    }

    public void writeString(int fieldNumber, String value) {
        writeTag(fieldNumber, WIRE_TYPE_LENGTH_DELIMITED);
        writeStringNoTag(value);
    }

    public void writeByteArray(int fieldNumber, byte[] value) {
        writeTag(fieldNumber, WIRE_TYPE_LENGTH_DELIMITED);
        writeBytesNoTag(value);
    }

    public void writeBytesNoTag(byte[] value) {
        writeUInt32NoTag(value.length);
        writeRawBytes(value);
    }

    public void writeInt32NoTag(int value) {
        if (value >= 0) {
            writeUInt32NoTag(value);
        } else {
            writeUInt64NoTag(value);
        }
    }

    public void writeUInt32NoTag(int value) {
        while (true) {
            if ((value & ~0x7F) == 0) {
                writeRaw((byte) value);
                return;
            } else {
                writeRaw((byte) ((value & 0x7F) | 0x80));
                value >>>= 7;
            }
        }
    }

    public void writeFixed32NoTag(int value) {
        writeRaw((byte) (value & 0xFF));
        writeRaw((byte) ((value >> 8) & 0xFF));
        writeRaw((byte) ((value >> 16) & 0xFF));
        writeRaw((byte) ((value >> 24) & 0xFF));
    }

    public void writeUInt64NoTag(long value) {
        while (true) {
            if ((value & ~0x7FL) == 0) {
                writeRaw((byte) value);
                return;
            } else {
                writeRaw((byte) (((int) value & 0x7F) | 0x80));
                value >>>= 7;
            }
        }
    }

    public void writeFixed64NoTag(long value) {
        writeRaw((byte) ((int) (value) & 0xFF));
        writeRaw((byte) ((int) (value >> 8) & 0xFF));
        writeRaw((byte) ((int) (value >> 16) & 0xFF));
        writeRaw((byte) ((int) (value >> 24) & 0xFF));
        writeRaw((byte) ((int) (value >> 32) & 0xFF));
        writeRaw((byte) ((int) (value >> 40) & 0xFF));
        writeRaw((byte) ((int) (value >> 48) & 0xFF));
        writeRaw((byte) ((int) (value >> 56) & 0xFF));
    }

    public void writeStringNoTag(String value) {
        writeUInt32NoTag(value.length());
        writeRawBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    public void writeRaw(byte value) {
        buffer.write(value);
    }

    @SneakyThrows
    private void writeRawBytes(byte[] value) {
        buffer.write(value);
    }
}
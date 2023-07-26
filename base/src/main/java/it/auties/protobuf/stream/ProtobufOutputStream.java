package it.auties.protobuf.stream;

import it.auties.protobuf.model.ProtobufWireType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class ProtobufOutputStream {
    private final ByteArrayOutputStream buffer;
    public ProtobufOutputStream(ByteArrayOutputStream buffer) {
        this.buffer = buffer;
    }

    public ProtobufOutputStream() {
        this(new ByteArrayOutputStream());
    }

    static int makeTag(int fieldNumber, int wireType) {
        return (fieldNumber << 3) | wireType;
    }

    public void writeTag(int fieldNumber, int wireType) {
        writeUInt32NoTag(makeTag(fieldNumber, wireType));
    }

    public void writeInt32(int fieldNumber, int value) {
        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_VAR_INT);
        writeInt32NoTag(value);
    }

    public void writeInt32(int fieldNumber, Integer value) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_VAR_INT);
        writeInt32NoTag(value);
    }

    public void writeUInt32(int fieldNumber, int value) {
        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_VAR_INT);
        writeUInt32NoTag(value);
    }

    public void writeUInt32(int fieldNumber, Integer value) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_VAR_INT);
        writeUInt32NoTag(value);
    }

    public void writeFloat(int fieldNumber, float value) {
        writeFixed32(fieldNumber, Float.floatToRawIntBits(value));
    }

    public void writeFloat(int fieldNumber, Float value) {
        if(value == null){
            return;
        }

        writeFixed32(fieldNumber, Float.floatToRawIntBits(value));
    }

    public void writeFixed32(int fieldNumber, int value) {
        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_FIXED32);
        writeFixed32NoTag(value);
    }

    public void writeFixed32(int fieldNumber, Integer value) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_FIXED32);
        writeFixed32NoTag(value);
    }

    public void writeInt64(int fieldNumber, long value) {
        writeUInt64(fieldNumber, value);
    }

    public void writeInt64(int fieldNumber, Long value) {
        if(value == null){
            return;
        }

        writeUInt64(fieldNumber, value);
    }

    public void writeUInt64(int fieldNumber, long value) {
        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_VAR_INT);
        writeUInt64NoTag(value);
    }

    public void writeUInt64(int fieldNumber, Long value) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_VAR_INT);
        writeUInt64NoTag(value);
    }

    public void writeDouble(int fieldNumber, double value) {
        writeFixed64(fieldNumber, Double.doubleToRawLongBits(value));
    }

    public void writeDouble(int fieldNumber, Double value) {
        if(value == null){
            return;
        }

        writeFixed64(fieldNumber, Double.doubleToRawLongBits(value));
    }

    public void writeFixed64(int fieldNumber, long value) {
        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_FIXED64);
        writeFixed64NoTag(value);
    }

    public void writeFixed64(int fieldNumber, Long value) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_FIXED64);
        writeFixed64NoTag(value);
    }

    public void writeBool(int fieldNumber, boolean value) {
        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_VAR_INT);
        writeRaw((byte) (value ? 1 : 0));
    }

    public void writeBool(int fieldNumber, Boolean value) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_VAR_INT);
        writeRaw((byte) (value ? 1 : 0));
    }

    public void writeString(int fieldNumber, String value) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeStringNoTag(value);
    }

    public void writeBytes(int fieldNumber, byte[] value) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeBytesNoTag(value);
    }

    public byte[] toByteArray() {
        return buffer.toByteArray();
    }

    private void writeBytesNoTag(byte[] value) {
        writeUInt32NoTag(value.length);
        writeRawBytes(value);
    }

    private void writeInt32NoTag(int value) {
        if (value >= 0) {
            writeUInt32NoTag(value);
        } else {
            writeUInt64NoTag(value);
        }
    }

    private void writeUInt32NoTag(int value) {
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

    private void writeFixed32NoTag(int value) {
        writeRaw((byte) (value & 0xFF));
        writeRaw((byte) ((value >> 8) & 0xFF));
        writeRaw((byte) ((value >> 16) & 0xFF));
        writeRaw((byte) ((value >> 24) & 0xFF));
    }

    private void writeUInt64NoTag(long value) {
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

    private void writeFixed64NoTag(long value) {
        writeRaw((byte) ((int) (value) & 0xFF));
        writeRaw((byte) ((int) (value >> 8) & 0xFF));
        writeRaw((byte) ((int) (value >> 16) & 0xFF));
        writeRaw((byte) ((int) (value >> 24) & 0xFF));
        writeRaw((byte) ((int) (value >> 32) & 0xFF));
        writeRaw((byte) ((int) (value >> 40) & 0xFF));
        writeRaw((byte) ((int) (value >> 48) & 0xFF));
        writeRaw((byte) ((int) (value >> 56) & 0xFF));
    }

    private void writeStringNoTag(String value) {
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        writeUInt32NoTag(bytes.length);
        writeRawBytes(bytes);
    }

    private void writeRaw(byte value) {
        buffer.write(value);
    }

    private void writeRawBytes(byte[] value) {
        try {
            buffer.write(value);
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot write bytes to stream", exception);
        }
    }
}

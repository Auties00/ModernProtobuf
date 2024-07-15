package it.auties.protobuf.stream;

import it.auties.protobuf.model.ProtobufWireType;

import java.nio.charset.StandardCharsets;
import java.util.Collection;

public final class ProtobufOutputStream {
    public static int getFieldSize(int fieldNumber, int wireType) {
        return getVarIntSizeUnsigned(ProtobufWireType.makeTag(fieldNumber, wireType));
    }

    public static int getVarIntSize(int value) {
        if(value >= 0) {
            return getVarIntSizeUnsigned(value);
        }

        return getVarIntSize((long) value);
    }

    public static int getVarIntSizeUnsigned(int value) {
        if (value >= 0 && value <= 127) {
            return 1;
        } else if (value >= 128 && value <= 16383) {
            return 2;
        } else if (value >= 16384 && value <= 2097151) {
            return 3;
        } else if (value >= 2097152 && value <= 268435455) {
            return 4;
        } else {
            return 5;
        }
    }

    public static int getVarIntSize(long value) {
        var counter = 0;
        while (true) {
            counter++;
            if ((value & ~0x7FL) == 0) {
                return counter;
            } else {
                value >>>= 7;
            }
        }
    }

    public static int getStringSize(String value) {
        if(value == null) {
            return 0;
        }

        var length = value.getBytes(StandardCharsets.UTF_8).length;
        return getVarIntSizeUnsigned(length) + length;
    }

    public static int getBytesSize(byte[] value) {
        if(value == null) {
            return 0;
        }

        return getVarIntSizeUnsigned(value.length) + value.length;
    }

    private final byte[] buffer;
    private int position;
    public ProtobufOutputStream(int size) {
        this.buffer = new byte[size];
    }

    private void writeTag(int fieldNumber, int wireType) {
        writeUInt32NoTag(ProtobufWireType.makeTag(fieldNumber, wireType));
    }

    public void writeInt32(int fieldNumber, Collection<Integer> values) {
        if(values == null){
            return;
        }

        for (var value : values) {
            writeInt32(fieldNumber, value);
        }
    }
    
    public void writeInt32(int fieldNumber, Integer value) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_VAR_INT);
        writeInt32NoTag(value);
    }
    
    public void writeUInt32(int fieldNumber, Collection<Integer> values) {
        if(values == null){
            return;
        }

        for (var value : values) {
            writeUInt32(fieldNumber, value);
        }
    }

    public void writeUInt32(int fieldNumber, Integer value) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_VAR_INT);
        writeUInt32NoTag(value);
    }

    public void writeFloat(int fieldNumber, Collection<Float> values) {
        if(values == null){
            return;
        }

        for (var value : values) {
            writeFloat(fieldNumber, value);
        }
    }
    
    public void writeFloat(int fieldNumber, Float value) {
        if(value == null){
            return;
        }

        writeFixed32(fieldNumber, Float.floatToRawIntBits(value));
    }

    public void writeFixed32(int fieldNumber, Collection<Integer> values) {
        if(values == null){
            return;
        }

        for (var value : values) {
            writeFixed32(fieldNumber, value);
        }
    }
    
    public void writeFixed32(int fieldNumber, Integer value) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_FIXED32);
        writeFixed32NoTag(value);
    }

    public void writeInt64(int fieldNumber, Collection<Long> values) {
        if(values == null){
            return;
        }

        for (var value : values) {
            writeInt64(fieldNumber, value);
        }
    }
    
    public void writeInt64(int fieldNumber, Long value) {
        if(value == null){
            return;
        }

        writeUInt64(fieldNumber, value);
    }

    public void writeUInt64(int fieldNumber, Collection<Long> values) {
        if(values == null){
            return;
        }

        for (var value : values) {
            writeUInt64(fieldNumber, value);
        }
    }
    
    public void writeUInt64(int fieldNumber, Long value) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_VAR_INT);
        writeUInt64NoTag(value);
    }

    public void writeDouble(int fieldNumber, Collection<Double> values) {
        if(values == null){
            return;
        }

        for (var value : values) {
            writeDouble(fieldNumber, value);
        }
    }
    
    public void writeDouble(int fieldNumber, Double value) {
        if(value == null){
            return;
        }

        writeFixed64(fieldNumber, Double.doubleToRawLongBits(value));
    }

    public void writeFixed64(int fieldNumber, Collection<Long> values) {
        if(values == null){
            return;
        }

        for (var value : values) {
            writeFixed64(fieldNumber, value);
        }
    }

    public void writeFixed64(int fieldNumber, Long value) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_FIXED64);
        writeFixed64NoTag(value);
    }

    public void writeBool(int fieldNumber, Collection<Boolean> values) {
        if(values == null){
            return;
        }

        for (var value : values) {
            writeBool(fieldNumber, value);
        }
    }

    public void writeBool(int fieldNumber, Boolean value) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_VAR_INT);
        write((byte) (value ? 1 : 0));
    }

    public void writeString(int fieldNumber, Collection<String> values) {
        if(values == null){
            return;
        }

        for (var value : values) {
            writeString(fieldNumber, value);
        }
    }

    public void writeString(int fieldNumber, String value) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeStringNoTag(value);
    }

    public void writeBytes(int fieldNumber, Collection<byte[]> values) {
        if(values == null){
            return;
        }

        for (var value : values) {
            writeBytes(fieldNumber, value);
        }
    }

    public void writeBytes(int fieldNumber, byte[] value) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeBytesNoTag(value);
    }

    public byte[] toByteArray() {
        return buffer;
    }

    private void writeBytesNoTag(byte[] value) {
        writeUInt32NoTag(value.length);
        write(value);
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
                write((byte) value);
                return;
            } else {
                write((byte) ((value & 0x7F) | 0x80));
                value >>>= 7;
            }
        }
    }

    private void writeFixed32NoTag(int value) {
        write((byte) (value & 0xFF));
        write((byte) ((value >> 8) & 0xFF));
        write((byte) ((value >> 16) & 0xFF));
        write((byte) ((value >> 24) & 0xFF));
    }

    private void writeUInt64NoTag(long value) {
        while (true) {
            if ((value & ~0x7FL) == 0) {
                write((byte) value);
                return;
            } else {
                write((byte) (((int) value & 0x7F) | 0x80));
                value >>>= 7;
            }
        }
    }

    private void writeFixed64NoTag(long value) {
        write((byte) ((int) (value) & 0xFF));
        write((byte) ((int) (value >> 8) & 0xFF));
        write((byte) ((int) (value >> 16) & 0xFF));
        write((byte) ((int) (value >> 24) & 0xFF));
        write((byte) ((int) (value >> 32) & 0xFF));
        write((byte) ((int) (value >> 40) & 0xFF));
        write((byte) ((int) (value >> 48) & 0xFF));
        write((byte) ((int) (value >> 56) & 0xFF));
    }

    private void writeStringNoTag(String value) {
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        writeUInt32NoTag(bytes.length);
        write(bytes);
    }

    private void write(byte value) {
        buffer[position++] = value;
    }

    private void write(byte[] values) {
        for (var value : values) {
            buffer[position++] = value;
        }
    }
}

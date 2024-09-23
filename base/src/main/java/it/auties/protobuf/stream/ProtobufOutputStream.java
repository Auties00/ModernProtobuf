package it.auties.protobuf.stream;

import it.auties.protobuf.exception.ProtobufSerializationException;
import it.auties.protobuf.model.ProtobufString;
import it.auties.protobuf.model.ProtobufWireType;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Collection;

public final class ProtobufOutputStream {
    public static int getFieldSize(int fieldNumber, int wireType) {
        return getVarIntSize(ProtobufWireType.makeTag(fieldNumber, wireType));
    }

    // Long values go from [-2^63, 2^63)
    // A negative var-int always take up 10 bits
    // A positive var int takes up log_2(value) / 7 + 1
    // Constants were folded here to save time
    public static int getVarIntSize(long value) {
        if(value < 0) {
            return 10;
        }else if (value < 128) {
            return 1;
        } else if (value < 16384) {
            return 2;
        } else if (value < 2097152) {
            return 3;
        } else if (value < 268435456) {
            return 4;
        } else if(value < 34359738368L) {
            return 5;
        }else if(value < 4398046511104L) {
            return 6;
        }else if(value < 562949953421312L) {
            return 7;
        }else if(value < 72057594037927936L) {
            return 8;
        }else {
            return 9;
        }
    }

    public static int getStringSize(ProtobufString value) {
        var count = value.encodedLength();
        return getVarIntSize(count) + count;
    }

    public static int getBytesSize(ByteBuffer value) {
        if(value == null) {
            return 0;
        }

        return getVarIntSize(value.remaining()) + value.remaining();
    }

    public static int getVarIntPackedSize(int fieldNumber, Collection<? extends Number> values) {
        if(values == null){
            return 0;
        }

        var size = getFieldSize(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);;
        var valueSize = 0;
        for (var value : values) {
            valueSize += getVarIntSize(value.longValue());
        }
        size += getVarIntSize(valueSize);
        size += valueSize;
        return size;
    }

    public int getFixed32PackedSize(int fieldNumber, Collection<? extends Number> values) {
        if(values == null){
            return 0;
        }

        var valuesSize = values.size() * 4;
        return getFieldSize(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED)
                + getVarIntSize(valuesSize)
                + valuesSize;
    }

    public static int getFixed64PackedSize(int fieldNumber, Collection<? extends Number> values) {
        if(values == null){
            return 0;
        }

        var valuesSize = values.size() * 8;
        return getFieldSize(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED)
                + getVarIntSize(valuesSize)
                + valuesSize;
    }

    public static int getBoolPackedSize(int fieldNumber, Collection<Long> values) {
        if(values == null){
            return 0;
        }

        var valuesSize = values.size();
        return getFieldSize(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED)
                + getVarIntSize(valuesSize)
                + valuesSize;
    }

    private final Output output;
    public ProtobufOutputStream(int size) {
        this.output = Output.allocate(size);
    }

    public ProtobufOutputStream(OutputStream outputStream) {
        this.output = Output.wrap(outputStream);
    }

    private void writeTag(int fieldNumber, int wireType) {
        writeVarIntNoTag(ProtobufWireType.makeTag(fieldNumber, wireType));
    }

    public void writeGroupStart(int fieldNumber) {
        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_START_OBJECT);
    }

    public void writeGroupEnd(int fieldNumber) {
        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_END_OBJECT);
    }

    public void writeInt32Packed(int fieldNumber, Collection<Integer> values) {
        if(values == null){
            return;
        }

        var size = 0;
        for (var value : values) {
            size += getVarIntSize(value);
        }
        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeVarIntNoTag(size);
        for (var value : values) {
            writeVarIntNoTag(value);
        }
    }
    
    public void writeInt32(int fieldNumber, Integer value) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_VAR_INT);
        writeVarIntNoTag(value);
    }
    
    public void writeUInt32Packed(int fieldNumber, Collection<Integer> values) {
        if(values == null){
            return;
        }

        var size = 0;
        for (var value : values) {
            size += getVarIntSize(value);
        }
        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeVarIntNoTag(size);
        for (var value : values) {
            writeVarIntNoTag(value);
        }
    }

    public void writeUInt32(int fieldNumber, Integer value) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_VAR_INT);
        writeVarIntNoTag(value);
    }

    public void writeFloatPacked(int fieldNumber, Collection<Float> values) {
        if(values == null){
            return;
        }

        var size = values.size() * 4;
        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeVarIntNoTag(size);
        for (var value : values) {
            writeFixed32NoTag(Float.floatToRawIntBits(value));
        }
    }
    
    public void writeFloat(int fieldNumber, Float value) {
        if(value == null){
            return;
        }

        writeFixed32(fieldNumber, Float.floatToRawIntBits(value));
    }

    public void writeFixed32Packed(int fieldNumber, Collection<Integer> values) {
        if(values == null){
            return;
        }

        var size = values.size() * 4;
        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeVarIntNoTag(size);
        for (var value : values) {
            writeFixed32NoTag(value);
        }
    }
    
    public void writeFixed32(int fieldNumber, Integer value) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_FIXED32);
        writeFixed32NoTag(value);
    }

    private void writeFixed32NoTag(Integer value) {
        output.write((byte) (value & 0xFF));
        output.write((byte) ((value >> 8) & 0xFF));
        output.write((byte) ((value >> 16) & 0xFF));
        output.write((byte) ((value >> 24) & 0xFF));
    }

    public void writeInt64Packed(int fieldNumber, Collection<Long> values) {
        if(values == null){
            return;
        }

        var size = values.size() * 8;
        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeVarIntNoTag(size);
        for (var value : values) {
            writeFixed64NoTag(value);
        }
    }
    
    public void writeInt64(int fieldNumber, Long value) {
        if(value == null){
            return;
        }

        writeUInt64(fieldNumber, value);
    }

    public void writeUInt64Packed(int fieldNumber, Collection<Long> values) {
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
        writeVarIntNoTag(value);
    }

    public void writeDoublePacked(int fieldNumber, Collection<Double> values) {
        if(values == null){
            return;
        }

        var size = values.size() * 8;
        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeVarIntNoTag(size);
        for (var value : values) {
            writeFixed64NoTag(Double.doubleToRawLongBits(value));
        }
    }
    
    public void writeDouble(int fieldNumber, Double value) {
        if(value == null){
            return;
        }

        writeFixed64(fieldNumber, Double.doubleToRawLongBits(value));
    }

    public void writeFixed64Packed(int fieldNumber, Collection<Long> values) {
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

    private void writeFixed64NoTag(Long value) {
        output.write((byte) ((int) ((long) value) & 0xFF));
        output.write((byte) ((int) (value >> 8) & 0xFF));
        output.write((byte) ((int) (value >> 16) & 0xFF));
        output.write((byte) ((int) (value >> 24) & 0xFF));
        output.write((byte) ((int) (value >> 32) & 0xFF));
        output.write((byte) ((int) (value >> 40) & 0xFF));
        output.write((byte) ((int) (value >> 48) & 0xFF));
        output.write((byte) ((int) (value >> 56) & 0xFF));
    }

    public void writeBoolPacked(int fieldNumber, Collection<Boolean> values) {
        if(values == null){
            return;
        }

        var size = values.size();
        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeVarIntNoTag(size);
        for (var value : values) {
            output.write((byte)( value ? 1 : 0));
        }
    }

    public void writeBool(int fieldNumber, Boolean value) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_VAR_INT);
        output.write((byte) (value ? 1 : 0));
    }

    public void writeString(int fieldNumber, ProtobufString value) {
        if(value == null){
            return;
        }

        value.write(fieldNumber, this);
    }

    public void writeBytes(int fieldNumber, ByteBuffer value) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        var size = value.remaining();
        writeVarIntNoTag(size);
        output.write(value);
    }

    public void writeBytes(int fieldNumber, byte[] value) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeVarIntNoTag(value.length);
        output.write(value);
    }

    public void writeBytes(int fieldNumber, byte[] value, int offset, int size) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeVarIntNoTag(size);
        output.write(value, offset, size);
    }

    public void writeObject(int fieldNumber, int size) {
        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        writeVarIntNoTag(size);
    }

    public byte[] toByteArray() {
        return output.toByteArray();
    }

    private void writeVarIntNoTag(long value) {
        while (true) {
            if ((value & ~0x7FL) == 0) {
                output.write((byte) value);
                return;
            } else {
                output.write((byte) (((int) value & 0x7F) | 0x80));
                value >>>= 7;
            }
        }
    }

    private static abstract sealed class Output {
        public abstract void write(byte entry);
        public abstract void write(byte[] entry);
        public abstract void write(byte[] entry, int offset, int length);
        public abstract void write(ByteBuffer entry);
        public abstract byte[] toByteArray();

        private static Output wrap(OutputStream outputStream) {
            return new Stream(outputStream);
        }

        private static Output allocate(int size) {
            return new Bytes(size);
        }

        private static final class Stream extends Output {
            private final OutputStream outputStream;
            private Stream(OutputStream outputStream) {
                this.outputStream = outputStream;
            }

            @Override
            public void write(byte entry) {
                try {
                    outputStream.write(entry);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public void write(byte[] entry) {
                try {
                    outputStream.write(entry);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public void write(byte[] entry, int offset, int length) {
                try {
                    outputStream.write(entry, offset, length);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public void write(ByteBuffer entry) {
                try {
                    var size = entry.remaining();
                    var bufferPosition = entry.position();
                    for(var i = 0; i < size; i++) {
                        outputStream.write(entry.get(bufferPosition + i));
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            @Override
            public byte[] toByteArray() {
                throw new UnsupportedOperationException();
            }
        }

        private static final class Bytes extends Output {
            private final byte[] buffer;
            private int position;
            private Bytes(int size) {
                this.buffer = new byte[size];
            }

            @Override
            public void write(byte entry) {
                buffer[position++] = entry;
            }

            @Override
            public void write(byte[] entry) {
                for (byte b : entry) {
                    buffer[position++] = b;
                }
            }

            @Override
            public void write(byte[] entry, int offset, int length) {
                for(var i = 0; i < length; i++) {
                    buffer[position++] = entry[offset + i];
                }
            }

            @Override
            public void write(ByteBuffer entry) {
                var size = entry.remaining();
                var bufferPosition = entry.position();
                for(var i = 0; i < size; i++) {
                    buffer[position++] = entry.get(bufferPosition + i);
                }
            }

            @Override
            public byte[] toByteArray() {
                if(position != buffer.length) {
                    throw ProtobufSerializationException.sizeMismatch();
                }

                return buffer;
            }
        }
    }
}

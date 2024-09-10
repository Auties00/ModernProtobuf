package it.auties.protobuf.stream;

import it.auties.protobuf.exception.ProtobufSerializationException;
import it.auties.protobuf.model.ProtobufString;
import it.auties.protobuf.model.ProtobufWireType;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

public final class ProtobufOutputStream {
    public static int getFieldSize(int fieldNumber, int wireType) {
        return getVarIntSize(ProtobufWireType.makeTag(fieldNumber, wireType));
    }

    // Long values go from [-2^63, 2^63)
    // A negative var-int always take up 10 bits
    // A positive var int takes up log_2(value) / 7 + 1
    // Constants where folded here to save time
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

    // Adapted from https://stackoverflow.com/a/8512877
    // Tested other alternatives including Guava's, seems the fastest considering all possibilities
    public static int getStringSize(String value) {
        if(value == null) {
            return 0;
        }

        var count = 0;
        for (int i = 0, len = value.length(); i < len; i++) {
            var ch = value.charAt(i);
            if (ch <= 0x7F) {
                count++;
            } else if (ch <= 0x7FF) {
                count += 2;
            } else if (Character.isHighSurrogate(ch)) {
                count += 4;
                ++i;
            } else {
                count += 3;
            }
        }
        return getVarIntSize(count) + count;
    }

    public static int getBytesSize(byte[] value) {
        if(value == null) {
            return 0;
        }

        return getVarIntSize(value.length) + value.length;
    }

    public static int getBytesSize(ByteBuffer value) {
        if(value == null) {
            return 0;
        }

        return getVarIntSize(value.remaining()) + value.remaining();
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
        writeVarIntNoTag(value);
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
        writeVarIntNoTag(value);
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
        output.write((byte) (value & 0xFF));
        output.write((byte) ((value >> 8) & 0xFF));
        output.write((byte) ((value >> 16) & 0xFF));
        output.write((byte) ((value >> 24) & 0xFF));
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
        writeVarIntNoTag(value);
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
        output.write((byte) ((int) ((long) value) & 0xFF));
        output.write((byte) ((int) (value >> 8) & 0xFF));
        output.write((byte) ((int) (value >> 16) & 0xFF));
        output.write((byte) ((int) (value >> 24) & 0xFF));
        output.write((byte) ((int) (value >> 32) & 0xFF));
        output.write((byte) ((int) (value >> 40) & 0xFF));
        output.write((byte) ((int) (value >> 48) & 0xFF));
        output.write((byte) ((int) (value >> 56) & 0xFF));
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
        output.write((byte) (value ? 1 : 0));
    }

    public void writeString(int fieldNumber, Collection<?> values) {
        if(values == null){
            return;
        }

        for (var value : values) {
            switch (value) {
                case String string -> writeString(fieldNumber, string);
                case ProtobufString protobufString -> writeString(fieldNumber, protobufString);
                default -> throw new IllegalStateException("Unexpected value: " + value);
            }
        }
    }

    public void writeString(int fieldNumber, ProtobufString value) {
        if(value == null){
            return;
        }

        value.write(fieldNumber, this);
    }

    public void writeString(int fieldNumber, String value) {
        if(value == null){
            return;
        }

        writeTag(fieldNumber, ProtobufWireType.WIRE_TYPE_LENGTH_DELIMITED);
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarIntNoTag(bytes.length);
        output.write(bytes);
    }

    public void writeBytes(int fieldNumber, Collection<?> values) {
        if(values == null){
            return;
        }

        for (var value : values) {
            switch (value) {
                case byte[] bytes -> writeBytes(fieldNumber, bytes);
                case ByteBuffer byteBuffer -> writeBytes(fieldNumber, byteBuffer);
                default -> throw new IllegalStateException("Unexpected value: " + value);
            }
        }
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

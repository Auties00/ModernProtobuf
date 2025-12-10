package it.auties.protobuf.builtin;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufMixin;
import it.auties.protobuf.annotation.ProtobufSerializer;
import it.auties.protobuf.io.ProtobufReader;
import it.auties.protobuf.io.ProtobufWriter;

import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;

@SuppressWarnings("unused")
@ProtobufMixin
public final class StringMixin {
    @ProtobufDeserializer
    public static String ofNullable(ProtobufReader reader) {
        var length = reader.readLengthDelimitedPropertyLength();
        return switch (reader.rawDataTypePreference()) {
            case BYTE_ARRAY -> {
                var source = reader.readRawBytes(length);
                yield new String(source, StandardCharsets.UTF_8);
            }

            case BYTE_BUFFER -> {
                var source = reader.readRawBuffer(length);
                if(source.hasArray()) {
                    yield new String(source.array(), source.arrayOffset() + source.position(), source.remaining(), StandardCharsets.UTF_8);
                }else {
                    var copy = new byte[length];
                    source.get(copy);
                    yield new String(copy, StandardCharsets.UTF_8);
                }
            }

            case MEMORY_SEGMENT -> {
                var source = reader.readRawMemorySegment(length);
                yield source.getString(0, StandardCharsets.UTF_8);
            }
        };
    }

    @ProtobufSerializer
    public static void toValue(ProtobufWriter<?> writer, String value) {
        if (value != null) {
            var source = value.getBytes(StandardCharsets.UTF_8);
            writer.writeLengthDelimitedPropertyLength(source.length);
            writer.writeRawBytes(source);
        }
    }
}

package it.auties.protobuf.model;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum ProtobufType {
    UNKNOWN(null, null, false),
    MESSAGE(byte[].class, byte[].class, false),
    ENUM(Integer.class, Integer.class, false),
    GROUP(byte[].class, byte[].class, false),
    MAP(Map.class, Map.class, false),
    FLOAT(float.class, Float.class, true),
    DOUBLE(double.class, Double.class, true),
    BOOL(boolean.class, Boolean.class, true),
    STRING(ProtobufString.Lazy.class, ProtobufString.class, false),
    BYTES(ByteBuffer.class, ByteBuffer.class, false),
    INT32(int.class, Integer.class, true),
    SINT32(int.class, Integer.class, true),
    UINT32(int.class, Integer.class, true),
    FIXED32(int.class, Integer.class, true),
    SFIXED32(int.class, Integer.class, true),
    INT64(long.class, Long.class, true),
    SINT64(long.class, Long.class, true),
    UINT64(long.class, Long.class, true),
    FIXED64(long.class, Long.class, true),
    SFIXED64(long.class, Long.class, true);

    private static final Map<String, ProtobufType> BY_NAME = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(entry -> entry.name().toLowerCase(), Function.identity()));

    private final Class<?> serializedType;
    private final Class<?> deserializableType;
    private final boolean packable;
    ProtobufType(Class<?> serializedType, Class<?> deserializableType, boolean packable) {
        this.serializedType = serializedType;
        this.deserializableType = deserializableType;
        this.packable = packable;
    }

    public static ProtobufType of(String name) {
        return name == null ? UNKNOWN : BY_NAME.getOrDefault(name.toLowerCase(), UNKNOWN);
    }

    public Class<?> serializedType() {
        if(this == UNKNOWN) {
            throw new UnsupportedOperationException();
        }

        return serializedType;
    }

    public Class<?> deserializableType() {
        if(this == UNKNOWN) {
            throw new UnsupportedOperationException();
        }

        return deserializableType;
    }

    public boolean isPackable() {
        return this.packable;
    }

    public boolean isPrimitive() {
        return switch (this) {
            case UNKNOWN, MESSAGE, ENUM, GROUP, MAP -> false;
            case FLOAT, DOUBLE, BOOL, STRING, BYTES, INT32, SINT32, UINT32,
                 FIXED32, SFIXED32, INT64, SINT64, SFIXED64, UINT64, FIXED64 -> true;
        };
    }
}

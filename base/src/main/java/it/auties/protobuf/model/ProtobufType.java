package it.auties.protobuf.model;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public enum ProtobufType {
    UNKNOWN(null, null, false),
    MESSAGE(byte[].class, null, false),
    ENUM(Integer.class, null, false),
    GROUP(byte[].class, null, false),
    MAP(Map.class, null, false),
    FLOAT(float.class, Float.class, true),
    DOUBLE(double.class, Double.class, true),
    BOOL(boolean.class, Boolean.class, true),
    STRING(ProtobufString.class, null, false),
    BYTES(ByteBuffer.class, null, false),
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

    private final Class<?> serializedType;
    private final Class<?> wrapperType;
    private final boolean packable;
    ProtobufType(Class<?> serializedType, Class<?> descriptorType, boolean packable) {
        this.serializedType = serializedType;
        this.wrapperType = descriptorType;
        this.packable = packable;
    }

    public static Optional<ProtobufType> of(String name) {
        return Arrays.stream(values())
                .filter(entry -> entry.name().toLowerCase().equals(name))
                .findFirst();
    }

    public Class<?> serializedType() {
        if(this == UNKNOWN) {
            throw new UnsupportedOperationException();
        }

        return this.serializedType;
    }

    public Class<?> wrapperType() {
        if(this == UNKNOWN) {
            throw new UnsupportedOperationException();
        }

        return Objects.requireNonNullElse(this.wrapperType, this.serializedType);
    }

    public boolean isPackable() {
        return this.packable;
    }

    public boolean isObject() {
        return this == MESSAGE
                || this == ENUM
                || this == GROUP;
    }
}

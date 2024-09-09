package it.auties.protobuf.model;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

public enum ProtobufType {
    UNKNOWN(null, null, null, false),
    OBJECT(Object.class, Object.class, null, false),
    GROUP(Object.class, Object.class, null, false),
    MAP(Map.class, Map.class, null, false),
    FLOAT(float.class, Float.class, null, true),
    DOUBLE(double.class, Double.class, null, true),
    BOOL(boolean.class, Boolean.class, null, true),
    STRING(ProtobufString.class, ProtobufString.class, String.class, false),
    BYTES(ByteBuffer.class, ByteBuffer.class, byte[].class, false),
    INT32(int.class, Integer.class, null, true),
    SINT32(int.class, Integer.class, null, true),
    UINT32(int.class, Integer.class, null, true),
    FIXED32(int.class, Integer.class, null, true),
    SFIXED32(int.class, Integer.class, null, true),
    INT64(long.class, Long.class, null, true),
    SINT64(long.class, Long.class, null, true),
    UINT64(long.class, Long.class, null, true),
    FIXED64(long.class, Long.class, null, true),
    SFIXED64(long.class, Long.class, null, true);

    private final Class<?> primitiveType;
    private final Class<?> wrappedType;
    private final Class<?> wrappedTypeSerializationAlias;
    private final boolean packable;

    ProtobufType(Class<?> primitiveType, Class<?> wrappedType, Class<?> wrappedTypeSerializationAlias, boolean packable) {
        this.primitiveType = primitiveType;
        this.wrappedType = wrappedType;
        this.wrappedTypeSerializationAlias = wrappedTypeSerializationAlias;
        this.packable = packable;
    }

    public static Optional<ProtobufType> of(String name) {
        return Arrays.stream(values())
                .filter(entry -> entry.name().toLowerCase().equals(name))
                .findFirst();
    }

    public Class<?> primitiveType() {
        if(this == UNKNOWN) {
            throw new UnsupportedOperationException();
        }

        return this.primitiveType;
    }

    public Class<?> wrappedType() {
        if(this == UNKNOWN) {
            throw new UnsupportedOperationException();
        }

        return this.wrappedType;
    }

    public Optional<Class<?>> wrappedTypeSerializationAlias() {
        if(this == UNKNOWN) {
            throw new UnsupportedOperationException();
        }

        return Optional.ofNullable(this.wrappedTypeSerializationAlias);
    }

    public boolean isPackable() {
        if(this == UNKNOWN) {
            throw new UnsupportedOperationException();
        }

        return this.packable;
    }
}

package it.auties.protobuf.model;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

public enum ProtobufType {
    UNKNOWN(null, null, new Class[0], false),
    OBJECT(Object.class, Object.class, new Class[0], false),
    GROUP(Map.class, Map.class, new Class[]{Integer.class, Object.class}, false),
    MAP(Map.class, Map.class, new Class[0], false),
    FLOAT(float.class, Float.class, new Class[0], true),
    DOUBLE(double.class, Double.class, new Class[0], true),
    BOOL(boolean.class, Boolean.class, new Class[0], true),
    STRING(ProtobufString.class, ProtobufString.class, new Class[0], false),
    BYTES(ByteBuffer.class, ByteBuffer.class, new Class[0], false),
    INT32(int.class, Integer.class, new Class[0], true),
    SINT32(int.class, Integer.class, new Class[0], true),
    UINT32(int.class, Integer.class, new Class[0], true),
    FIXED32(int.class, Integer.class, new Class[0], true),
    SFIXED32(int.class, Integer.class, new Class[0], true),
    INT64(long.class, Long.class, new Class[0], true),
    SINT64(long.class, Long.class, new Class[0], true),
    UINT64(long.class, Long.class, new Class[0], true),
    FIXED64(long.class, Long.class, new Class[0], true),
    SFIXED64(long.class, Long.class, new Class[0], true);

    private final Class<?> primitiveType;
    private final Class<?> wrappedType;
    private final Class<?>[] wrappedTypeParameters;
    private final boolean packable;

    ProtobufType(Class<?> primitiveType, Class<?> wrappedType, Class<?>[] wrappedTypeParameters, boolean packable) {
        this.primitiveType = primitiveType;
        this.wrappedType = wrappedType;
        this.wrappedTypeParameters = wrappedTypeParameters;
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

    public Class<?>[] wrappedTypeParameters() {
        if(this == UNKNOWN) {
            throw new UnsupportedOperationException();
        }

        return this.wrappedTypeParameters;
    }

    public boolean isPackable() {
        if(this == UNKNOWN) {
            throw new UnsupportedOperationException();
        }

        return this.packable;
    }
}

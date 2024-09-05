package it.auties.protobuf.model;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

public enum ProtobufType {
    UNKNOWN(null, null, false),
    OBJECT(Object.class, Object.class, false),
    @Deprecated
    GROUP(Object.class, Object.class, false),
    MAP(Map.class, Map.class, false),
    FLOAT(float.class, Float.class, true),
    DOUBLE(double.class, Double.class, true),
    BOOL(boolean.class, Boolean.class, true),
    STRING(String.class, String.class, false),
    BYTES(byte[].class, byte[].class, false),
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

    private final Class<?> primitiveType;

    private final Class<?> wrappedType;

    private final boolean packable;

    ProtobufType(Class<?> primitiveType, Class<?> wrappedType, boolean packable) {
        this.primitiveType = primitiveType;
        this.wrappedType = wrappedType;
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

    public boolean isPackable() {
        if(this == UNKNOWN) {
            throw new UnsupportedOperationException();
        }

        return this.packable;
    }
}

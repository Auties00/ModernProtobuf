package it.auties.protobuf.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

import java.util.*;

@Getter
@AllArgsConstructor
@Accessors(fluent = true)
public enum ProtobufType {
    MESSAGE(Object.class, Object.class, false),
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

    public static Optional<ProtobufType> of(String name) {
        return Arrays.stream(values())
                .filter(entry -> entry.name().equalsIgnoreCase(name))
                .findFirst();
    }

    @NonNull
    private final Class<?> primitiveType;

    @NonNull
    private final Class<?> wrappedType;

    private final boolean isPackable;
}

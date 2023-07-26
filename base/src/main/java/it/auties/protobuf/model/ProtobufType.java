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
    MESSAGE(Object.class, Object.class),
    ENUM(Enum.class, Enum.class),
    FLOAT(float.class, Float.class),
    DOUBLE(double.class, Double.class),
    BOOL(boolean.class, Boolean.class),
    STRING(String.class, String.class),
    BYTES(byte[].class, byte[].class),
    INT32(int.class, Integer.class),
    SINT32(int.class, Integer.class),
    UINT32(int.class, Integer.class),
    FIXED32(int.class, Integer.class),
    SFIXED32(int.class, Integer.class),
    INT64(long.class, Long.class),
    SINT64(long.class, Long.class),
    UINT64(long.class, Long.class),
    FIXED64(long.class, Long.class),
    SFIXED64(long.class, Long.class);

    public static Optional<ProtobufType> of(String name) {
        return Arrays.stream(values())
                .filter(entry -> entry.name().equalsIgnoreCase(name))
                .findFirst();
    }

    @NonNull
    private final Class<?> primitiveType;

    @NonNull
    private final Class<?> wrappedType;
}

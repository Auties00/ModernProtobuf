package it.auties.protobuf.base;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

import java.util.*;

@AllArgsConstructor
@Accessors(fluent = true)
public enum ProtobufType {
    MESSAGE(ProtobufMessage.class, ProtobufMessage.class),
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

    @Getter
    @NonNull
    private final Class<?> primitiveType;

    @Getter
    @NonNull
    private final Class<?> wrappedType;

    public boolean isMessage() {
        return this == MESSAGE;
    }

    public boolean isInt() {
        return this == INT32
                || this == SINT32
                || this == UINT32
                || this == FIXED32
                || this == SFIXED32;
    }

    public boolean isLong() {
        return this == INT64
                || this == SINT64
                || this == UINT64
                || this == FIXED64
                || this == SFIXED64;
    }

    public boolean isFloat(){
        return this == FLOAT;
    }

    public boolean isDouble(){
        return this == DOUBLE;
    }

    public boolean isFloatingPoint(){
        return isFloat() || isDouble();
    }

    public boolean isBool() {
        return this == BOOL;
    }
}

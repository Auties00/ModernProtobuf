package it.auties.protobuf.extension;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

public class OptionalExtension {
    public static OptionalInt ofNullableInt(Integer value) {
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }

    public static OptionalLong ofNullableLong(Long value) {
        return value == null ? OptionalLong.empty() : OptionalLong.of(value);
    }

    public static OptionalDouble ofNullableDouble(Double value) {
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static Integer toNullableInt(OptionalInt value) {
        return value.isEmpty() ? null : value.getAsInt();
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static Long toNullableLong(OptionalLong value) {
        return value.isEmpty() ? null : value.getAsLong();
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static Double toNullableDouble(OptionalDouble value) {
        return value.isEmpty() ? null : value.getAsDouble();
    }
}

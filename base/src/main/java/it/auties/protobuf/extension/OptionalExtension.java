package it.auties.protobuf.extension;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

@SuppressWarnings({"OptionalAssignedToNull", "OptionalUsedAsFieldOrParameterType"})
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

    public static Integer toNullableInt(OptionalInt value) {
        return value == null ? null : value.isEmpty() ? null : value.getAsInt();
    }

    public static Long toNullableLong(OptionalLong value) {
        return value == null ? null : value.isEmpty() ? null : value.getAsLong();
    }

    public static Double toNullableDouble(OptionalDouble value) {
        return value == null ? null : value.isEmpty() ? null : value.getAsDouble();
    }
}

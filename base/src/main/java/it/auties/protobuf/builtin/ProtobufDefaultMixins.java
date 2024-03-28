package it.auties.protobuf.builtin;

import it.auties.protobuf.annotation.ProtobufConverter;
import it.auties.protobuf.annotation.ProtobufDefaultValue;
import it.auties.protobuf.model.ProtobufMixin;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings({"OptionalAssignedToNull", "OptionalUsedAsFieldOrParameterType", "unused"})
public class ProtobufDefaultMixins implements ProtobufMixin {
    @ProtobufDefaultValue
    public static AtomicInteger newAtomicInt() {
        return new AtomicInteger();
    }

    @ProtobufDefaultValue
    public static AtomicLong newAtomicLong() {
        return new AtomicLong();
    }

    @ProtobufDefaultValue
    public static AtomicBoolean newAtomicBoolean() {
        return new AtomicBoolean();
    }

    @ProtobufDefaultValue
    public static AtomicBoolean newAtomicReference() {
        return new AtomicBoolean();
    }

    @ProtobufConverter
    public static AtomicInteger ofAtomic(Integer value) {
        return value == null ? new AtomicInteger() : new AtomicInteger(value);
    }

    @ProtobufConverter
    public static AtomicLong ofAtomic(Long value) {
        return value == null ? new AtomicLong() : new AtomicLong(value);
    }

    @ProtobufConverter
    public static AtomicBoolean ofAtomic(Boolean value) {
        return value == null ? new AtomicBoolean() : new AtomicBoolean(value);
    }

    @ProtobufConverter
    public static <T> AtomicReference<T> ofNullableReference(T value) {
        return new AtomicReference<>(value);
    }

    @ProtobufConverter
    public static int toInt(AtomicInteger value) {
        return value.get();
    }

    @ProtobufConverter
    public static long toLong(AtomicLong value) {
        return value.get();
    }

    @ProtobufConverter
    public static boolean toBoolean(AtomicBoolean value) {
        return value.get();
    }

    @ProtobufConverter
    public static <T> T toValue(AtomicReference<T> value) {
        return value.get();
    }

    @ProtobufDefaultValue
    public static <T> Optional<T> newOptional() {
        return Optional.empty();
    }

    @ProtobufDefaultValue
    public static OptionalInt newOptionalInt() {
        return OptionalInt.empty();
    }

    @ProtobufDefaultValue
    public static OptionalLong newOptionalLong() {
        return OptionalLong.empty();
    }

    @ProtobufDefaultValue
    public static OptionalDouble newOptionalDouble() {
        return OptionalDouble.empty();
    }

    @ProtobufConverter
    public static OptionalInt ofOptional(Integer value) {
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }

    @ProtobufConverter
    public static OptionalLong ofOptional(Long value) {
        return value == null ? OptionalLong.empty() : OptionalLong.of(value);
    }

    @ProtobufConverter
    public static OptionalDouble ofOptional(Double value) {
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    @ProtobufConverter
    public static Integer toNullableInt(OptionalInt value) {
        return value == null ||  value.isEmpty() ? null : value.getAsInt();
    }

    @ProtobufConverter
    public static Long toNullableLong(OptionalLong value) {
        return value == null || value.isEmpty() ? null : value.getAsLong();
    }

    @ProtobufConverter
    public static Double toNullableDouble(OptionalDouble value) {
        return value == null || value.isEmpty() ? null : value.getAsDouble();
    }

    @ProtobufConverter
    public static UUID ofNullable(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    @ProtobufConverter
    public static String toValue(UUID value) {
        return value == null ? null : value.toString();
    }
}

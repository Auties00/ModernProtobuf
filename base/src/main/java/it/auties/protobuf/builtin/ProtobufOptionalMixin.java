package it.auties.protobuf.builtin;

import it.auties.protobuf.annotation.ProtobufDefaultValue;
import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufSerializer;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import static it.auties.protobuf.annotation.ProtobufDeserializer.BuilderBehaviour.OVERRIDE;

@SuppressWarnings({"OptionalAssignedToNull", "OptionalUsedAsFieldOrParameterType", "unused"})
public class ProtobufOptionalMixin {
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

    @ProtobufDeserializer(builderBehaviour = OVERRIDE)
    public static <T> Optional<T> ofOptional(T value) {
        return Optional.ofNullable(value);
    }

    @ProtobufDeserializer(builderBehaviour = OVERRIDE)
    public static OptionalInt ofOptional(Integer value) {
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }

    @ProtobufDeserializer(builderBehaviour = OVERRIDE)
    public static OptionalLong ofOptional(Long value) {
        return value == null ? OptionalLong.empty() : OptionalLong.of(value);
    }

    @ProtobufDeserializer(builderBehaviour = OVERRIDE)
    public static OptionalDouble ofOptional(Double value) {
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    @ProtobufSerializer
    public static <T> T toNullableValue(Optional<T> value) {
        return value == null ? null : value.orElse(null);
    }

    @ProtobufSerializer
    public static Integer toNullableInt(OptionalInt value) {
        return value == null ||  value.isEmpty() ? null : value.getAsInt();
    }

    @ProtobufSerializer
    public static Long toNullableLong(OptionalLong value) {
        return value == null || value.isEmpty() ? null : value.getAsLong();
    }

    @ProtobufSerializer
    public static Double toNullableDouble(OptionalDouble value) {
        return value == null || value.isEmpty() ? null : value.getAsDouble();
    }
}

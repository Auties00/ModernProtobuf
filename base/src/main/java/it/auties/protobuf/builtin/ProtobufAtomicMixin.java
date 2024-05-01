package it.auties.protobuf.builtin;

import it.auties.protobuf.annotation.ProtobufDefaultValue;
import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufSerializer;
import it.auties.protobuf.model.ProtobufMixin;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static it.auties.protobuf.annotation.ProtobufDeserializer.BuilderBehaviour.OVERRIDE;

@SuppressWarnings("unused")
public class ProtobufAtomicMixin implements ProtobufMixin {
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
    public static <T> AtomicReference<T> newAtomicReference() {
        return new AtomicReference<>();
    }

    @ProtobufDeserializer(builderBehaviour = OVERRIDE)
    public static AtomicInteger ofAtomic(Integer value) {
        return value == null ? new AtomicInteger() : new AtomicInteger(value);
    }

    @ProtobufDeserializer(builderBehaviour = OVERRIDE)
    public static AtomicLong ofAtomic(Long value) {
        return value == null ? new AtomicLong() : new AtomicLong(value);
    }

    @ProtobufDeserializer(builderBehaviour = OVERRIDE)
    public static AtomicBoolean ofAtomic(Boolean value) {
        return value == null ? new AtomicBoolean() : new AtomicBoolean(value);
    }

    @ProtobufDeserializer(builderBehaviour = OVERRIDE)
    public static <T> AtomicReference<T> ofAtomic(T value) {
        return new AtomicReference<>(value);
    }

    @ProtobufSerializer
    public static int toInt(AtomicInteger value) {
        return value.get();
    }

    @ProtobufSerializer
    public static long toLong(AtomicLong value) {
        return value.get();
    }

    @ProtobufSerializer
    public static boolean toBoolean(AtomicBoolean value) {
        return value.get();
    }

    @ProtobufSerializer
    public static <T> T toValue(AtomicReference<T> value) {
        return value.get();
    }
}

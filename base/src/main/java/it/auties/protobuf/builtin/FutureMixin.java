package it.auties.protobuf.builtin;

import it.auties.protobuf.annotation.ProtobufDefaultValue;
import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufMixin;
import it.auties.protobuf.annotation.ProtobufSerializer;

import java.util.concurrent.CompletableFuture;

@SuppressWarnings("unused")
@ProtobufMixin
public final class FutureMixin {
    @ProtobufDefaultValue
    public static <T> CompletableFuture<T> newCompletableFuture() {
        return CompletableFuture.completedFuture(null);
    }

    @ProtobufDeserializer
    public static <T> CompletableFuture<T> ofNullable(T value) {
        return CompletableFuture.completedFuture(value);
    }

    @ProtobufSerializer
    public static <T> T toValue(CompletableFuture<T> future) {
        return future == null ? null : future.getNow(null);
    }
}

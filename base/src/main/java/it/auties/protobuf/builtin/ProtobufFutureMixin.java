package it.auties.protobuf.builtin;

import it.auties.protobuf.annotation.ProtobufDefaultValue;
import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufSerializer;

import java.util.concurrent.CompletableFuture;

import static it.auties.protobuf.annotation.ProtobufDeserializer.BuilderBehaviour.OVERRIDE;

@SuppressWarnings("unused")
public class ProtobufFutureMixin {
    @ProtobufDefaultValue
    public static <T> CompletableFuture<T> newCompletableFuture() {
        return CompletableFuture.completedFuture(null);
    }

    @ProtobufDeserializer(builderBehaviour = OVERRIDE)
    public static <T> CompletableFuture<T> ofNullable(T value) {
        return CompletableFuture.completedFuture(value);
    }

    @ProtobufSerializer
    public static <T> T toValue(CompletableFuture<T> future) {
        return future == null ? null : future.getNow(null);
    }
}

package it.auties.protobuf.serialization.performance.model;

import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public record ProtobufAccessors(Function<?, Object> getter,
                                @SuppressWarnings("rawtypes") BiConsumer setter,
                                @SuppressWarnings("rawtypes") Supplier<Collection> repeatedField,
                                ProtobufField record) {
    public ProtobufAccessors(Function<?, Object> getter, @SuppressWarnings("rawtypes") BiConsumer setter, ProtobufField record) {
        this(getter, setter, null, record);
    }
}

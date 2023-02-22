package it.auties.protobuf.serialization.performance.model;

import java.util.function.BiConsumer;
import java.util.function.Function;

public record ProtobufAccessors(Function<?, Object> getter, @SuppressWarnings("rawtypes") BiConsumer setter,
                                ProtobufField record) {
}

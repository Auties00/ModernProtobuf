package it.auties.protobuf.serialization.performance.model;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

@SuppressWarnings("rawtypes")
public record ProtobufProperties(Supplier<?> builder, Function<?, ?> build,
                                 Map<Integer, Function<?, Object>> getters,
                                 Map<Integer, BiConsumer> setters,
                                 Function<?, Object> converter,
                                 Map<Integer, ProtobufEntry> records) {
}

package it.auties.protobuf.serialization.performance.model;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public record ProtobufModel(Supplier<?> builder, Function<?, ?> build, Map<Integer, ProtobufAccessors> accessors) {
}

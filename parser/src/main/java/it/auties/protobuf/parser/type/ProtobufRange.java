package it.auties.protobuf.parser.type;

public sealed interface ProtobufRange {
    record Bounded(ProtobufInteger min, ProtobufInteger max) implements ProtobufRange {

    }

    record LowerBounded(ProtobufInteger min) implements ProtobufRange {

    }
}

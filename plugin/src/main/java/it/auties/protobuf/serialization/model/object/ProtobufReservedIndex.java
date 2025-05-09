package it.auties.protobuf.serialization.model.object;

public sealed interface ProtobufReservedIndex {
    boolean allows(int index);

    record Range(int min, int max) implements ProtobufReservedIndex {
        @Override
        public boolean allows(int index) {
            return index < min || index > max;
        }
    }

    record Value(int value) implements ProtobufReservedIndex {
        @Override
        public boolean allows(int index) {
            return index != value;
        }
    }
}

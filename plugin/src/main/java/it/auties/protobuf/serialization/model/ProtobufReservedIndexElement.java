package it.auties.protobuf.serialization.model;

public sealed interface ProtobufReservedIndexElement {
    boolean allows(int index);

    record Range(int min, int max) implements ProtobufReservedIndexElement {
        @Override
        public boolean allows(int index) {
            return index < min || index > max;
        }
    }

    record Value(int value) implements ProtobufReservedIndexElement {
        @Override
        public boolean allows(int index) {
            return index != value;
        }
    }
}

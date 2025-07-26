package it.auties.protobuf.serialization.model;

public sealed interface ProtobufReservedElement {
    record Name(String name) implements ProtobufReservedElement {
        public boolean allows(String name) {
            return !this.name.equals(name);
        }
    }

    sealed interface Index extends ProtobufReservedElement {
        boolean allows(int index);

        record Range(int min, int max) implements Index {
            @Override
            public boolean allows(int index) {
                return index < min || index > max;
            }
        }

        record Value(int value) implements Index {
            @Override
            public boolean allows(int index) {
                return index != value;
            }
        }
    }
}

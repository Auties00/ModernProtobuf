package it.auties.proto.conversion;

import it.auties.protobuf.annotation.ProtobufConverter;

record Wrapper(String value) {
    @ProtobufConverter
    public static Wrapper of(String object) {
        return new Wrapper(object);
    }

    @ProtobufConverter
    public String toValue() {
        return value;
    }
}

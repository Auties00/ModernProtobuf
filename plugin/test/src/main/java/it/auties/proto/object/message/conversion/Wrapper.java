package it.auties.proto.object.message.conversion;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufSerializer;

record Wrapper(String value) {
    @ProtobufDeserializer
    public static Wrapper of(String object) {
        return new Wrapper(object);
    }

    @ProtobufSerializer
    public String toValue() {
        return value;
    }
}

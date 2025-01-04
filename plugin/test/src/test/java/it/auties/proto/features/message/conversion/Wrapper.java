package it.auties.proto.features.message.conversion;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufSerializer;
import it.auties.protobuf.model.ProtobufString;

// ProtobufString -> String -> Wrapper
// Wrapper -> String -> ProtobufString
record Wrapper(ProtobufString value) {
    @ProtobufDeserializer
    public static Wrapper of(ProtobufString object) {
        return new Wrapper(object);
    }

    @ProtobufSerializer
    public ProtobufString toValue() {
        return value;
    }
}

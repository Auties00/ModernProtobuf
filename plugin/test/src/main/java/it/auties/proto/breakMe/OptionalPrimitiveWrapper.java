package it.auties.proto.breakMe;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.annotation.ProtobufSerializer;
import it.auties.protobuf.model.ProtobufType;

import java.util.Optional;

@ProtobufMessage
public record OptionalPrimitiveWrapper(
        @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
        Optional<Wrapper> wrapper
) {
    public record Wrapper(byte[] data) {
        @ProtobufDeserializer
        public static Wrapper of(byte[] data) {
            return new Wrapper(data);
        }

        @ProtobufSerializer
        public byte[] data() {
            return data;
        }
    }
}

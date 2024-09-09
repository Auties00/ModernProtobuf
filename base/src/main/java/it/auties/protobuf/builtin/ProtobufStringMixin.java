package it.auties.protobuf.builtin;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufMixin;
import it.auties.protobuf.model.ProtobufString;

@ProtobufMixin
public class ProtobufStringMixin {
    @ProtobufDeserializer
    public static String toString(ProtobufString string) {
        return string.toString();
    }
}

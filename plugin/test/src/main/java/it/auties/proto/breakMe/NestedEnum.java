package it.auties.proto.breakMe;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.concurrent.CompletableFuture;

@ProtobufMessage
public record NestedEnum(
        @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
        CompletableFuture<CompletableFuture<CompletableFuture<CompletableFuture<CompletableFuture<Enum>>>>> value
) {
        @ProtobufEnum
        public enum Enum {
                FIRST,
                SECOND,
                THIRD
        }
}
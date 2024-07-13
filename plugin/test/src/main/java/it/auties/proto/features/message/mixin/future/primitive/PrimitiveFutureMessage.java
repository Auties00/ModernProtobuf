package it.auties.proto.features.message.mixin.future.primitive;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.concurrent.CompletableFuture;

@ProtobufMessage
public record PrimitiveFutureMessage(
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        CompletableFuture<String> content
) {

}

package it.auties.proto.features.message.mixin.future.object;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.concurrent.CompletableFuture;

@ProtobufMessage
public record ObjectFutureMessage(
        @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
        CompletableFuture<VersionMessage> content,
        @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
        CompletableFuture<CompletableFuture<VersionMessage>> nestedTwoFuture
) {

}

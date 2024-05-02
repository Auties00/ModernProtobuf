package it.auties.proto.object.message.future;

import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufMessage;
import it.auties.protobuf.model.ProtobufType;

import java.util.concurrent.CompletableFuture;

public record FutureMessage(
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        CompletableFuture<String> content
) implements ProtobufMessage {

}

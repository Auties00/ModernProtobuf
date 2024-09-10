package it.auties.proto.features.message.mixin.future.primitive;

import it.auties.protobuf.model.ProtobufString;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

public class PrimitiveFutureTest {
    @Test
    public void test() {
        var mapMessage = new PrimitiveFutureMessage(CompletableFuture.completedFuture(ProtobufString.wrap("Value")));
        var encoded = PrimitiveFutureMessageSpec.encode(mapMessage);
        Assertions.assertEquals(PrimitiveFutureMessageSpec.decode(encoded).content().getNow(null), mapMessage.content().getNow(null));
    }
}

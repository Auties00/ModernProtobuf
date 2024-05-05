package it.auties.proto.object.message.mixin.future.primitive;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

public class PrimitiveFutureTest {
    @Test
    public void test() {
        var mapMessage = new PrimitiveFutureMessage(CompletableFuture.completedFuture("Value"));
        var encoded = PrimitiveFutureMessageSpec.encode(mapMessage);
        Assertions.assertEquals(PrimitiveFutureMessageSpec.decode(encoded).content().getNow(null), mapMessage.content().getNow(null));
    }
}

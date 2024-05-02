package it.auties.proto.object.message.future;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

public class FutureTest {
    @Test
    public void test() {
        var mapMessage = new FutureMessage(CompletableFuture.completedFuture("Value"));
        var encoded = FutureMessageSpec.encode(mapMessage);
        Assertions.assertEquals(FutureMessageSpec.decode(encoded).content().getNow(null), mapMessage.content().getNow(null));
    }
}

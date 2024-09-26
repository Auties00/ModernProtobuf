package it.auties.proto.features.message.mixin.future.object;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

public class ObjectFutureTest {
    @Test
    public void test() {
        var mapMessage = new ObjectFutureMessageBuilder()
                .content(new VersionMessage(3, 5))
                .nestedTwoFuture(new VersionMessage(3, 5))
                .build();
        var encoded = ObjectFutureMessageSpec.encode(mapMessage);
        var decoded = ObjectFutureMessageSpec.decode(encoded);
        Assertions.assertEquals(
                decoded.content().getNow(null),
                mapMessage.content().getNow(null)
        );
        Assertions.assertEquals(
                decoded.nestedTwoFuture().getNow(CompletableFuture.completedFuture(null)).getNow(null),
                mapMessage.nestedTwoFuture().getNow(CompletableFuture.completedFuture(null)).getNow(null)
        );
    }
}

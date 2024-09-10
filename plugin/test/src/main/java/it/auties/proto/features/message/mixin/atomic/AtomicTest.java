package it.auties.proto.features.message.mixin.atomic;

import it.auties.protobuf.model.ProtobufString;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AtomicTest {
    @Test
    public void testBuilder() {
        var result = new AtomicMessageBuilder()
                .atomicString(ProtobufString.wrap("abc"))
                .atomicInteger(123)
                .atomicLong(456L)
                .atomicBoolean(true)
                .build();
        var encoded = AtomicMessageSpec.encode(result);
        var decoded = AtomicMessageSpec.decode(encoded);
        Assertions.assertEquals(result, decoded);
    }
}

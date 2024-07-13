package it.auties.proto.features.message.mixin.optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class OptionalTest {
    @Test
    public void testBuilder() {
        var resultBuilder = new OptionalMessageBuilder()
                .optionalString("abc")
                .optionalInt(123)
                .optionalDouble(456D)
                .optionalLong(null);
        var result = resultBuilder.optionalMessage(resultBuilder.build())
                .build();
        var encoded = OptionalMessageSpec.encode(result);
        var decoded = OptionalMessageSpec.decode(encoded);
        Assertions.assertEquals(result, decoded);
    }
}

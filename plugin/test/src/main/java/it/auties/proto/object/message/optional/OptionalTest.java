package it.auties.proto.object.message.optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class OptionalTest {
    @Test
    public void testBuilder() {
        var result = new OptionalMessageBuilder()
                .optionalString("abc")
                .optionalInt(123)
                .optionalDouble(456D)
                .optionalLong(null)
                .build();
        var encoded = OptionalMessageSpec.encode(result);
        var decoded = OptionalMessageSpec.decode(encoded);
        Assertions.assertEquals(result, decoded);
    }
}

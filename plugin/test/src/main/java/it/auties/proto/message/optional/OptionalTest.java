package it.auties.proto.message.optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

public class OptionalTest {
    @Test
    public void testBuilder() {
        var result = new OptionalMessageBuilder()
                .optionalString("abc")
                .optionalInt(123)
                .optionalDouble(456D)
                .optionalLong(OptionalLong.empty())
                .build();
        var encoded = OptionalMessageSpec.encode(result);
        var decoded = OptionalMessageSpec.decode(encoded);
        Assertions.assertEquals(result, decoded);
    }
}

package it.auties.proto.features.message.mixin.optional;

import it.auties.protobuf.model.ProtobufString;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;

public class OptionalTest {
    @Test
    public void testBuilder() {
        var resultBuilder = new OptionalMessageBuilder()
                .optionalString(ProtobufString.wrap("abc"))
                .optionalInt(123)
                .optionalDouble(456D)
                .optionalLong(null);
        var result = resultBuilder.optionalMessage(resultBuilder.build())
                .build();
        var encoded = OptionalMessageSpec.encode(result);
        System.out.println(HexFormat.of().formatHex(encoded));
        var decoded = OptionalMessageSpec.decode(encoded);
        Assertions.assertEquals(result, decoded);
    }
}

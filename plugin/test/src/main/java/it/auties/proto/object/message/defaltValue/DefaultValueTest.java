package it.auties.proto.object.message.defaltValue;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DefaultValueTest {
    @Test
    public void testDefaultValue() {
        var defaultMessage = new WrapperMessageBuilder()
                .build();
        Assertions.assertEquals(defaultMessage.optionalMessage(), OptionalMessage.empty());
        var encoded = WrapperMessageSpec.encode(new WrapperMessage(null));
        var decoded = WrapperMessageSpec.decode(encoded);
        Assertions.assertEquals(decoded.optionalMessage(), OptionalMessage.empty());
    }
}

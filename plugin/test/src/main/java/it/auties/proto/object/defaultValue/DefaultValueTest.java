package it.auties.proto.object.defaultValue;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DefaultValueTest {
    @Test
    public void testDefaultValue() {
        var defaultMessage = new WrapperMessageBuilder()
                .build();
        Assertions.assertEquals(defaultMessage.optionalMessage(), OptionalMessage.empty());
    }
}

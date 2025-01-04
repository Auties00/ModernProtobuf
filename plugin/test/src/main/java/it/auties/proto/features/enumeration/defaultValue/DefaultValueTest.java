package it.auties.proto.features.enumeration.defaultValue;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DefaultValueTest {
    @Test
    public void testDefaultValue() {
        var defaultMessage = new WrapperMessageBuilder()
                .build();
        Assertions.assertEquals(defaultMessage.enumType(), EnumType.DEFAULT_VALUE);
        var encoded = WrapperMessageSpec.encode(new WrapperMessage(null));
        var decoded = WrapperMessageSpec.decode(encoded);
        Assertions.assertEquals(decoded.enumType(), EnumType.DEFAULT_VALUE);
    }
}

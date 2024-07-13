package it.auties.proto.features.message.unknownFields;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class UnknownFieldsTest {
    @Test
    public void testSimple() {
        var extendedMessage = new ExtendedMessageBuilder()
                .value("Hello World!")
                .extended(18)
                .build();
        var baseMessage = SimpleMessageSpec.decode(ExtendedMessageSpec.encode(extendedMessage));
        var unknownField = (Number) baseMessage.unknownFields().get(2);
        Assertions.assertEquals(unknownField.intValue(), extendedMessage.extended());
    }

    @Test
    public void testWrapper() {
        var extendedMessage = new ExtendedMessageBuilder()
                .value("Hello World!")
                .extended(18)
                .build();
        var baseMessage = WrapperMessageSpec.decode(ExtendedMessageSpec.encode(extendedMessage));
        var unknownField = baseMessage.unknownFields().get(2);
        Assertions.assertTrue(unknownField.isPresent());
        var unknownFieldValue = (Number) unknownField.get();
        Assertions.assertEquals(unknownFieldValue.intValue(), extendedMessage.extended());
    }

    @Test
    public void testMixed() {
        var extendedMessage = new ExtendedMessageBuilder()
                .value("Hello World!")
                .extended(18)
                .build();
        var baseMessage = MixedMessageSpec.decode(ExtendedMessageSpec.encode(extendedMessage));
        var unknownField = (Number) baseMessage.unknownFields().get(0);
        Assertions.assertEquals(unknownField.intValue(), extendedMessage.extended());
    }
}

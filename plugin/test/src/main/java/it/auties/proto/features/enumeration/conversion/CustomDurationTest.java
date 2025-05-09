package it.auties.proto.features.enumeration.conversion;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CustomDurationTest {
    @Test
    public void test() {
        var message = new CustomDurationMessage(CustomDuration.DAY);
        var encoded = CustomDurationMessageSpec.encode(message);
        var decoded = CustomDurationMessageSpec.decode(encoded);
        Assertions.assertEquals(message.duration(), decoded.duration());
    }
}

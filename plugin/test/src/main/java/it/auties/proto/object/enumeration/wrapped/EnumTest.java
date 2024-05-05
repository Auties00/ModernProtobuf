package it.auties.proto.object.enumeration.wrapped;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EnumTest {
    @Test
        public void testModifiers() {
        var someMessage = new DirectEnumMessage(EnumType.FIRST, EnumType.THIRD);
        var encoded = DirectEnumMessageSpec.encode(someMessage);
        var decoded = DirectEnumMessageSpec.decode(encoded);
        Assertions.assertEquals(someMessage.content(), decoded.content());
        Assertions.assertEquals(someMessage.content1(), decoded.content1());
    }
}

package it.auties.proto.message.missing;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MissingFieldTest {
    @Test
        public void testModifiers() {
        var someMessage = new SerializableMessage("Hello", "Hello", "Hello", "Hello");
        var encoded = SerializableMessageSpec.encode(someMessage);
        var decoded = DeserializableMessageSpec.decode(encoded);
        Assertions.assertEquals(someMessage.content2(), decoded.content2());
    }
}

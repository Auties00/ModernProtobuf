package it.auties.proto.message.packed;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class PackedTest {
    @Test
        public void testModifiers() {
        var someMessage = new PackedMessage(new ArrayList<>(List.of(1, 2, 3)));
        var encoded = PackedMessageSpec.encode(someMessage);
        var decoded = PackedMessageSpec.decode(encoded);
        Assertions.assertEquals(someMessage.content(), decoded.content());
    }
}

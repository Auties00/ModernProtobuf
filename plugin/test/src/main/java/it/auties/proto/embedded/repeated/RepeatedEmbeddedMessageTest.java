package it.auties.proto.embedded.repeated;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class RepeatedEmbeddedMessageTest {
    @Test
        public void testModifiers() {
        var finalMessage = new RepeatedEmbeddedMessage(new ArrayList<>(List.of(1, 2, 3)));
        var anotherFinalMessage = new RepeatedEmbeddedMessage(new ArrayList<>(List.of(4, 5, 6)));
        var lastFinalMessage = new RepeatedEmbeddedMessage(new ArrayList<>(List.of(7, 8, 9)));
        var anotherMessage = new RepeatedEmbeddedWrapperMessage(new ArrayList<>(List.of(finalMessage, anotherFinalMessage, lastFinalMessage)));
        var anotherAnotherMessage = new RepeatedEmbeddedWrapperMessage(new ArrayList<>(List.of(anotherFinalMessage)));
        var someMessage = new RepeatedWrapperMessage(new ArrayList<>(List.of(anotherMessage, anotherAnotherMessage)));
        var encoded = RepeatedWrapperMessageSpec.encode(someMessage);
        var decoded = RepeatedWrapperMessageSpec.decode(encoded);
        Assertions.assertEquals(someMessage.content(), decoded.content());
    }
}

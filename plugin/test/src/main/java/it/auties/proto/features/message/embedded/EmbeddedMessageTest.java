package it.auties.proto.features.message.embedded;

import it.auties.protobuf.model.ProtobufString;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EmbeddedMessageTest {
    @Test
        public void testModifiers() {
        var anotherMessage = new EmbeddedMessage(ProtobufString.wrap("Hello"));
        var someMessage = new WrapperMessage(anotherMessage);
        var encoded = WrapperMessageSpec.encode(someMessage);
        var decoded = WrapperMessageSpec.decode(encoded);
        Assertions.assertNotNull(decoded.content());
        Assertions.assertEquals(anotherMessage.content(), decoded.content().content());
    }
}

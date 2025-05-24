package it.auties.proto.ci;

import it.auties.proto.ci.message.embedded.WrapperMessageSpec;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufString;
import it.auties.protobuf.model.ProtobufType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static it.auties.protobuf.model.ProtobufType.STRING;

public class EmbeddedMessageTest {
    @Test
        public void testModifiers() {
        var anotherMessage = new WrapperMessage.EmbeddedMessage(ProtobufString.wrap("Hello"));
        var someMessage = new WrapperMessage(anotherMessage);
        var encoded = WrapperMessageSpec.encode(someMessage);
        var decoded = WrapperMessageSpec.decode(encoded);
        Assertions.assertNotNull(decoded.content());
        Assertions.assertEquals(anotherMessage.content(), decoded.content().content());
    }

    @ProtobufMessage
    public static record WrapperMessage(
            @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
            EmbeddedMessage content
    ) {

            @ProtobufMessage
            public static record EmbeddedMessage(
                    @ProtobufProperty(index = 3, type = STRING)
                    ProtobufString content
            ) {

            }
    }
}

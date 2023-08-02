package it.auties.proto;

import it.auties.protobuf.Protobuf;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufMessage;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static it.auties.protobuf.model.ProtobufType.OBJECT;
import static it.auties.protobuf.model.ProtobufType.STRING;

public class EmbeddedMessageTest {
    @Test
    @SneakyThrows
    public void testModifiers() {
        var anotherMessage = new AnotherMessage("Hello");
        var someMessage = new SomeMessage(anotherMessage);
        var encoded = Protobuf.writeMessage(someMessage);
        var decoded = Protobuf.readMessage(encoded, SomeMessage.class);
        Assertions.assertNotNull(decoded.content());
        Assertions.assertEquals(anotherMessage.content(), decoded.content().content());
    }


    public record SomeMessage(
            @ProtobufProperty(index = 1, type = OBJECT)
            AnotherMessage content
    ) implements ProtobufMessage {

    }

    public record AnotherMessage(
            @ProtobufProperty(index = 3, type = STRING)
            String content
    ) implements ProtobufMessage {

    }
}

package it.auties.proto;

import it.auties.protobuf.Protobuf;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufMessage;
import it.auties.protobuf.model.ProtobufType;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MixedNotationsTest {
    @Test
    @SneakyThrows
    public void testModifiers() {
        var someMessage = new SomeMessage("Hello, this is an automated test!");
        var encoded = Protobuf.writeMessage(someMessage);
        var decoded = Protobuf.readMessage(encoded, SomeMessage.class);
        Assertions.assertEquals(someMessage.content(), decoded.content());
    }

    public record SomeMessage(
            @ProtobufProperty(index = 1, type = ProtobufType.STRING)
            String content
    ) implements ProtobufMessage {

    }
}

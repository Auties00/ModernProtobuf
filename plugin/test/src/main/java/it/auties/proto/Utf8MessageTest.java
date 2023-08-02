package it.auties.proto;

import it.auties.protobuf.Protobuf;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufMessage;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static it.auties.protobuf.model.ProtobufType.STRING;

public class Utf8MessageTest {
    @Test
    @SneakyThrows
    public void testNormal() {
        var someMessage = new UtfMessage("abc");
        var encoded = Protobuf.writeMessage(someMessage);
        var decoded = Protobuf.readMessage(encoded, UtfMessage.class);
        Assertions.assertEquals(someMessage.content(), decoded.content());
    }

    @Test
    @SneakyThrows
    public void testUtf() {
        var someMessage = new UtfMessage("è");
        var encoded = Protobuf.writeMessage(someMessage);
        var decoded = Protobuf.readMessage(encoded, UtfMessage.class);
        Assertions.assertEquals(someMessage.content(), decoded.content());
    }

    public record UtfMessage(
            @ProtobufProperty(index = 1, type = STRING)
            String content
    ) implements ProtobufMessage {

    }
}

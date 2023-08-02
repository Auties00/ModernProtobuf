package it.auties.proto;

import it.auties.protobuf.Protobuf;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufMessage;
import it.auties.protobuf.model.ProtobufType;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class RepeatedEmbeddedMessageTest {
    @Test
    @SneakyThrows
    public void testModifiers() {
        var finalMessage = new FinalMessage(new ArrayList<>(List.of(1, 2, 3)));
        var anotherFinalMessage = new FinalMessage(new ArrayList<>(List.of(4, 5, 6)));
        var lastFinalMessage = new FinalMessage(new ArrayList<>(List.of(7, 8, 9)));
        var anotherMessage = new AnotherMessage(new ArrayList<>(List.of(finalMessage, anotherFinalMessage, lastFinalMessage)));
        var anotherAnotherMessage = new AnotherMessage(new ArrayList<>(List.of(anotherFinalMessage)));
        var someMessage = new SomeMessage(new ArrayList<>(List.of(anotherMessage, anotherAnotherMessage)));
        var encoded = Protobuf.writeMessage(someMessage);
        var decoded = Protobuf.readMessage(encoded, SomeMessage.class);
        Assertions.assertEquals(someMessage.content(), decoded.content());
    }

    public record SomeMessage(
            @ProtobufProperty(index = 1, type = ProtobufType.OBJECT, repeated = true)
            ArrayList<AnotherMessage> content
    ) implements ProtobufMessage {
    }


    public record AnotherMessage(
            @ProtobufProperty(index = 1, type = ProtobufType.OBJECT, repeated = true)
            ArrayList<FinalMessage> content
    ) implements ProtobufMessage {

    }

    public record FinalMessage(
            @ProtobufProperty(index = 1, type = ProtobufType.INT32, repeated = true)
            ArrayList<Integer> content
    ) implements ProtobufMessage {

    }
}

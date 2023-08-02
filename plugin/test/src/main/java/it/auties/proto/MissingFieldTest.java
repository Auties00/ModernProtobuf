package it.auties.proto;

import it.auties.protobuf.Protobuf;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufMessage;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static it.auties.protobuf.model.ProtobufType.STRING;

public class MissingFieldTest {
    @Test
    @SneakyThrows
    public void testModifiers() {
        var someMessage = new Serializable("Hello", "Hello", "Hello", "Hello");
        var encoded = Protobuf.writeMessage(someMessage);
        var decoded = Protobuf.readMessage(encoded, Deserializable.class);
        Assertions.assertEquals(someMessage.content2(), decoded.content2());
    }

    public record Serializable(
            @ProtobufProperty(index = 1, type = STRING)
            String content,

            @ProtobufProperty(index = 2, type = STRING) String content1,

            @ProtobufProperty(index = 3, type = STRING)
            String content2,

            @ProtobufProperty(index = 4, type = STRING)
            String content3
    ) implements ProtobufMessage {

    }

    public record Deserializable(
            @ProtobufProperty(index = 3, type = STRING)
            String content2
    ) implements ProtobufMessage {

    }
}

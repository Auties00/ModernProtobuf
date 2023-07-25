package it.auties.protobuf;

import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufProperty;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static it.auties.protobuf.base.ProtobufType.UINT32;

public class PackedTest implements Protobuf {
    @Test
    @SneakyThrows
    public void testModifiers() {
        var someMessage = new SomeMessage(new ArrayList<>(List.of(1, 2, 3)));
        var encoded = writeValueAsBytes(someMessage);
        var decoded = readMessage(encoded, SomeMessage.class);
        Assertions.assertEquals(someMessage.content(), decoded.content());
    }

    public record SomeMessage(
            @ProtobufProperty(index = 1, type = UINT32, repeated = true) ArrayList<Integer> content
    ) implements ProtobufMessage {

    }
}

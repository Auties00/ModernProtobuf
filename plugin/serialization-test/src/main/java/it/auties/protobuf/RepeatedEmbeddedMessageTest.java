package it.auties.protobuf;

import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufProperty;
import it.auties.protobuf.base.ProtobufType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class RepeatedEmbeddedMessageTest implements Protobuf {
    @Test
    @SneakyThrows
    public void testModifiers() {
        var finalMessage = new FinalMessage(new ArrayList<>(List.of(1, 2, 3)));
        var anotherFinalMessage = new FinalMessage(new ArrayList<>(List.of(4, 5, 6)));
        var lastFinalMessage = new FinalMessage(new ArrayList<>(List.of(7, 8, 9)));
        var anotherMessage = new AnotherMessage(new ArrayList<>(List.of(finalMessage, anotherFinalMessage, lastFinalMessage)));
        var anotherAnotherMessage = new AnotherMessage(new ArrayList<>(List.of(anotherFinalMessage)));
        var someMessage = new SomeMessage(new ArrayList<>(List.of(anotherMessage, anotherAnotherMessage)));
        var encoded = writeValueAsBytes(someMessage);
        var decoded = readMessage(encoded, SomeMessage.class);
        Assertions.assertEquals(someMessage.content(), decoded.content());
    }

    @AllArgsConstructor
    @Jacksonized
    @Data
    @Builder
    @Accessors(fluent = true)
    public static class SomeMessage implements ProtobufMessage {
        @ProtobufProperty(
                index = 1,
                type = ProtobufType.MESSAGE,
                repeated = true
        )
        private ArrayList<AnotherMessage> content;
    }


    @AllArgsConstructor
    @Jacksonized
    @Data
    @Builder
    @Accessors(fluent = true)
    public static class AnotherMessage implements ProtobufMessage {
        @ProtobufProperty(
                index = 1,
                type = ProtobufType.MESSAGE,
                repeated = true
        )
        private ArrayList<FinalMessage> content;
    }

    @AllArgsConstructor
    @Jacksonized
    @Data
    @Builder
    @Accessors(fluent = true)
    public static class FinalMessage implements ProtobufMessage {
        @ProtobufProperty(
                index = 1,
                type = ProtobufType.INT32,
                repeated = true
        )
        private ArrayList<Integer> content;
    }
}

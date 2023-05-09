package it.auties.protobuf;

import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufProperty;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static it.auties.protobuf.base.ProtobufType.STRING;

public class Utf8MessageTest implements TestProvider {
    @Test
    @SneakyThrows
    public void testNormal() {
        var someMessage = new UtfMessage("abc");
        var encoded = writeValueAsBytes(someMessage);
        var decoded = readMessage(encoded, UtfMessage.class);
        Assertions.assertEquals(someMessage.content(), decoded.content());
    }

    @Test
    @SneakyThrows
    public void testUtf() {
        var someMessage = new UtfMessage("è");
        var encoded = writeValueAsBytes(someMessage);
        var decoded = readMessage(encoded, UtfMessage.class);
        Assertions.assertEquals(someMessage.content(), decoded.content());
    }

    @Jacksonized
    @Builder
    @Data
    @Accessors(fluent = true)
    public static class UtfMessage implements ProtobufMessage {
        @ProtobufProperty(index = 1, type = STRING)
        private String content;
    }
}

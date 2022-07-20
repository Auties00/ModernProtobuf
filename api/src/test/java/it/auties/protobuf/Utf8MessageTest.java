package it.auties.protobuf;

import it.auties.protobuf.api.model.ProtobufMessage;
import it.auties.protobuf.api.model.ProtobufProperty;
import it.auties.protobuf.api.model.ProtobufSchema;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static it.auties.protobuf.api.model.ProtobufProperty.Type.STRING;

public class Utf8MessageTest implements TestProvider {
    @Test
    @SneakyThrows
    public void testNormal() {
        var someMessage = new UtfMessage("abc");
        var encoded = JACKSON.writeValueAsBytes(someMessage);
        var decoded = JACKSON.reader()
                .with(ProtobufSchema.of(UtfMessage.class))
                .readValue(encoded, UtfMessage.class);
        Assertions.assertEquals(someMessage.content(), decoded.content());
    }

    @Test
    @SneakyThrows
    public void testUtf() {
        var someMessage = new UtfMessage("è");
        var encoded = JACKSON.writeValueAsBytes(someMessage);
        var decoded = JACKSON.reader()
                .with(ProtobufSchema.of(UtfMessage.class))
                .readValue(encoded, UtfMessage.class);
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

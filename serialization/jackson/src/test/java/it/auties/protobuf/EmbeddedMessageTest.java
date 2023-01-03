package it.auties.protobuf;

import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufProperty;
import it.auties.protobuf.serialization.jackson.api.ProtobufSchema;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static it.auties.protobuf.base.ProtobufType.MESSAGE;
import static it.auties.protobuf.base.ProtobufType.STRING;

public class EmbeddedMessageTest implements TestProvider {
    @Test
    @SneakyThrows
    public void testModifiers() {
        var anotherMessage = new AnotherMessage("Hello");
        var someMessage = new SomeMessage(anotherMessage);
        var encoded = JACKSON.writeValueAsBytes(someMessage);
        var decoded = JACKSON.reader()
                .with(ProtobufSchema.of(SomeMessage.class))
                .readValue(encoded, SomeMessage.class);
        Assertions.assertNotNull(decoded.content());
        Assertions.assertEquals(anotherMessage.content(), decoded.content().content());
    }

    @Jacksonized
    @Builder
    @Data
    @Accessors(fluent = true)
    public static class SomeMessage implements ProtobufMessage {
        @ProtobufProperty(index = 1, type = MESSAGE)
        private AnotherMessage content;
    }

    @Jacksonized
    @Builder
    @Data
    @Accessors(fluent = true)
    public static class AnotherMessage implements ProtobufMessage {
        @ProtobufProperty(index = 3, type = STRING)
        private String content;
    }
}

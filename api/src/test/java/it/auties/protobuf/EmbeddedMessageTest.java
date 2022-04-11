package it.auties.protobuf;

import it.auties.protobuf.model.ProtobufMessage;
import it.auties.protobuf.model.ProtobufProperty;
import it.auties.protobuf.model.ProtobufSchema;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
        System.out.println(decoded);
        Assertions.assertEquals(anotherMessage.content(), decoded.content().content());
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Jacksonized
    @Data
    @Builder
    @Accessors(fluent = true)
    public static class SomeMessage implements ProtobufMessage {
        @ProtobufProperty(
                index = 1,
                type = ProtobufProperty.Type.MESSAGE,
                concreteType = AnotherMessage.class
        )
        private AnotherMessage content;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Jacksonized
    @Data
    @Builder
    @Accessors(fluent = true)
    public static class AnotherMessage implements ProtobufMessage {
        @ProtobufProperty(
                index = 3,
                type = ProtobufProperty.Type.STRING
        )
        private String content;
    }
}

package it.auties.protobuf;

import it.auties.protobuf.api.model.ProtobufMessage;
import it.auties.protobuf.api.model.ProtobufProperty;
import it.auties.protobuf.api.model.ProtobufSchema;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MissingFieldTest implements TestProvider {
    @Test
    @SneakyThrows
    public void testModifiers() {
        var someMessage = new Serializable("Hello", "Hello", "Hello", "Hello");
        var encoded = JACKSON.writeValueAsBytes(someMessage);
        var decoded = JACKSON.reader()
                .with(ProtobufSchema.of(Deserializable.class))
                .readValue(encoded, Deserializable.class);
        Assertions.assertEquals(someMessage.content2(), decoded.content2());
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Jacksonized
    @Data
    @Builder
    @Accessors(fluent = true)
    public static class Serializable implements ProtobufMessage {
        @ProtobufProperty(index = 1, type = ProtobufProperty.Type.STRING)
        private String content;

        @ProtobufProperty(index = 2, type = ProtobufProperty.Type.STRING)
        private String content1;

        @ProtobufProperty(index = 3, type = ProtobufProperty.Type.STRING)
        private String content2;

        @ProtobufProperty(index = 4, type = ProtobufProperty.Type.STRING)
        private String content3;
    }

    @AllArgsConstructor
    @Jacksonized
    @Data
    @Builder
    @Accessors(fluent = true)
    public static class Deserializable implements ProtobufMessage {
        @ProtobufProperty(index = 3, type = ProtobufProperty.Type.STRING)
        private String content2;
    }
}

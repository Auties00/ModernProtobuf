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
        var someMessage = new Serializable("Hello");
        var encoded = JACKSON.writeValueAsBytes(someMessage);
        JACKSON.reader()
                .with(ProtobufSchema.of(Deserializable.class))
                .readValue(encoded, Deserializable.class);
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
    }

    @AllArgsConstructor
    @Jacksonized
    @Data
    @Builder
    @Accessors(fluent = true)
    public static class Deserializable implements ProtobufMessage {

    }
}

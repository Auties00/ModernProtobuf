package it.auties.protobuf;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufProperty;
import it.auties.protobuf.base.ProtobufType;
import it.auties.protobuf.serialization.jackson.api.ProtobufSchema;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MixedNotationsTest implements TestProvider {
    @Test
    @SneakyThrows
    public void testModifiers() {
        var someMessage = new SomeMessage("Hello, this is an automated test!");
        var encoded = JACKSON.writeValueAsBytes(someMessage);
        var decoded = JACKSON.reader()
                .with(ProtobufSchema.of(SomeMessage.class))
                .readValue(encoded, SomeMessage.class);
        Assertions.assertEquals(someMessage.content(), decoded.content());
    }

    @Jacksonized
    @Builder
    @Data
    @Accessors(fluent = true)
    public static class SomeMessage implements ProtobufMessage {
        @JsonProperty("1")
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        private String content;
    }
}

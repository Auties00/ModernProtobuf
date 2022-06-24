package it.auties.protobuf;

import it.auties.protobuf.api.model.ProtobufMessage;
import it.auties.protobuf.api.model.ProtobufProperty;
import it.auties.protobuf.api.model.ProtobufSchema;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class PackedTest implements TestProvider {
    @Test
    @SneakyThrows
    public void testModifiers() {
        var someMessage = new SomeMessage(List.of(1, 2, 3));
        var encoded = JACKSON.writeValueAsBytes(someMessage);
        var decoded = JACKSON.reader()
                .with(ProtobufSchema.of(SomeMessage.class))
                .readValue(encoded, SomeMessage.class);
        Assertions.assertEquals(someMessage.content(), decoded.content());
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
                type = ProtobufProperty.Type.UINT32,
                repeated = true,
                packed = true
        )
        private List<Integer> content;

        public static class SomeMessageBuilder {
            public SomeMessageBuilder content(List<Integer> content) {
                if (this.content == null) this.content = new ArrayList<>();
                this.content.addAll(content);
                return this;
            }
        }
    }
}

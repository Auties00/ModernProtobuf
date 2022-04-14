package it.auties.protobuf;

import com.fasterxml.jackson.annotation.JsonCreator;
import it.auties.protobuf.api.model.ProtobufMessage;
import it.auties.protobuf.api.model.ProtobufProperty;
import it.auties.protobuf.api.model.ProtobufSchema;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

public class EnumTest implements TestProvider {
    @Test
    @SneakyThrows
    public void testModifiers() {
        var someMessage = new SomeMessage(Type.THIRD);
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
                type = ProtobufProperty.Type.MESSAGE,
                concreteType = Type.class
        )
        private Type content;
    }

    @AllArgsConstructor
    @Accessors(fluent = true)
    public enum Type implements ProtobufMessage{
        FIRST(0),
        SECOND(1),
        THIRD(2);

        @Getter
        private final int index;
    }
}

import it.auties.protobuf.Protobuf;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.exception.ProtobufDeserializationException;
import it.auties.protobuf.model.ProtobufType;
import it.auties.protobuf.stream.ProtobufInputStream;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

public class EnumTest {
    @Test
    @SneakyThrows
    public void testModifiers() {
        var someMessage = new SomeMessage(Type.FIRST, Type.THIRD);
        var encoded = Protobuf.writeMessage(someMessage);
        var decoded = Protobuf.readMessage(encoded, SomeMessage.class);
        Assertions.assertEquals(someMessage.content(), decoded.content());
        Assertions.assertEquals(someMessage.content1(), decoded.content1());
    }

    @AllArgsConstructor
    @Accessors(fluent = true)
    public enum Type {
        FIRST(0),
        SECOND(1),
        THIRD(2);

        @Getter
        private final int index;
    }

    @Jacksonized
    @Builder
    @Data
    @Accessors(fluent = true)
    public static class SomeMessage {
        @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
        private Type content;

        @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
        private Type content1;
    }
}

import it.auties.protobuf.Protobuf;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static it.auties.protobuf.model.ProtobufType.STRING;

public class MissingFieldTest {
    @Test
    @SneakyThrows
    public void testModifiers() {
        var someMessage = new Serializable("Hello", "Hello", "Hello", "Hello");
        var encoded = Protobuf.writeMessage(someMessage);
        var decoded = Protobuf.readMessage(encoded, Deserializable.class);
        Assertions.assertEquals(someMessage.content2(), decoded.content2());
    }

    @Jacksonized
    @Builder
    @Data
    @Accessors(fluent = true)
    @ProtobufMessage
    public static class Serializable {
        @ProtobufProperty(index = 1, type = STRING)
        private String content;

        @ProtobufProperty(index = 2, type = STRING)
        private String content1;

        @ProtobufProperty(index = 3, type = STRING)
        private String content2;

        @ProtobufProperty(index = 4, type = STRING)
        private String content3;
    }

    @Jacksonized
    @Builder
    @Data
    @Accessors(fluent = true)
    @ProtobufMessage
    public static class Deserializable {
        @ProtobufProperty(index = 3, type = STRING)
        private String content2;
    }
}

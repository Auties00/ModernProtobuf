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

import static it.auties.protobuf.model.ProtobufType.MESSAGE;
import static it.auties.protobuf.model.ProtobufType.STRING;

public class EmbeddedMessageTest {
    @Test
    @SneakyThrows
    public void testModifiers() {
        var anotherMessage = new AnotherMessage("Hello");
        var someMessage = new SomeMessage(anotherMessage);
        var encoded = Protobuf.writeMessage(someMessage);
        var decoded = Protobuf.readMessage(encoded, SomeMessage.class);
        Assertions.assertNotNull(decoded.content());
        Assertions.assertEquals(anotherMessage.content(), decoded.content().content());
    }

    @Jacksonized
    @Builder
    @Data
    @Accessors(fluent = true)
    @ProtobufMessage
    public static class SomeMessage {
        @ProtobufProperty(index = 1, type = MESSAGE)
        private AnotherMessage content;
    }

    @Jacksonized
    @Builder
    @Data
    @Accessors(fluent = true)
    @ProtobufMessage
    public static class AnotherMessage {
        @ProtobufProperty(index = 3, type = STRING)
        private String content;
    }
}

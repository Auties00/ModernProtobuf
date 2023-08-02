package it.auties.proto;

import it.auties.protobuf.Protobuf;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufEnum;
import it.auties.protobuf.model.ProtobufMessage;
import it.auties.protobuf.model.ProtobufType;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EmbeddedEnumTest {
    @Test
    @SneakyThrows
    public void testModifiers() {
        var anotherMessage = new AnotherMessage(Type.SECOND);
        var someMessage = new SomeMessage(anotherMessage);
        var encoded = Protobuf.writeMessage(someMessage);
        var decoded = Protobuf.readMessage(encoded, SomeMessage.class);
        Assertions.assertNotNull(decoded.content());
        Assertions.assertEquals(anotherMessage.type(), decoded.content().type());
    }

    @Getter
    @Accessors(fluent = true)
    public enum Type implements ProtobufEnum {
        FIRST(0),
        SECOND(1),
        THIRD(10);

        @ProtobufEnumIndex
        private final int index;

        Type(@ProtobufEnumIndex int index) {
            this.index = index;
        }
    }

    public record SomeMessage(
            @ProtobufProperty(index = 1, type = ProtobufType.OBJECT)
            AnotherMessage content
    ) implements ProtobufMessage {

    }

    public record AnotherMessage(
            @ProtobufProperty(index = 3, type = ProtobufType.OBJECT)
            Type type
    ) implements ProtobufMessage {

    }
}

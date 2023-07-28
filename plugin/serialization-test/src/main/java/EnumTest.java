import it.auties.protobuf.Protobuf;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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

    @Getter
    @Accessors(fluent = true)
    @ProtobufEnum
    public enum Type {
        FIRST(0),
        SECOND(1),
        THIRD(2);

        private final int index;
        Type(@ProtobufEnumIndex int index) {
            this.index = index;
        }
    }

    @Jacksonized
    @Builder
    @Data
    @Accessors(fluent = true)
    @ProtobufMessage
    public static class SomeMessage {
        @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
        private Type content;

        @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
        private Type content1;
    }
}

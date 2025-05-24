package it.auties.proto.ci;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EnumTest {
    @Test
        public void testSimple() {
        var someMessage = new Message(Enum.FIRST, Enum.THIRD, Enum.SECOND);
        var encoded = EnumConstantValuesTestMessageSpec.encode(someMessage);
        var decoded = EnumConstantValuesTestMessageSpec.decode(encoded);
        Assertions.assertEquals(someMessage.content(), decoded.content());
        Assertions.assertEquals(someMessage.content1(), decoded.content1());
        Assertions.assertEquals(someMessage.content2(), decoded.content2());
    }

    @Test
    public void testEmbedded() {
        var anotherMessage = new Message(Enum.SECOND, Enum.FIRST, Enum.THIRD);
        var someMessage = new MessageWrapper(anotherMessage);
        var encoded = EnumConstantValuesTestMessageWrapperSpec.encode(someMessage);
        var decoded = EnumConstantValuesTestMessageWrapperSpec.decode(encoded);
        Assertions.assertNotNull(decoded.content());
        Assertions.assertEquals(someMessage.content().content(), decoded.content().content());
        Assertions.assertEquals(someMessage.content().content1(), decoded.content().content1());
        Assertions.assertEquals(someMessage.content().content2(), decoded.content().content2());
    }

    @ProtobufMessage
    record MessageWrapper(
            @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
            Message content
    ) {

    }

    @ProtobufMessage
    record Message(
            @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
            Enum content,
            @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
            Enum content1,
            @ProtobufProperty(index = 3, type = ProtobufType.ENUM)
            Enum content2
    ) {

    }

    @ProtobufEnum
    enum Enum {
        FIRST(0),
        SECOND(1),
        THIRD(10);

        final int index;

        Enum(@ProtobufEnumIndex int index) {
            this.index = index;
        }
    }
}

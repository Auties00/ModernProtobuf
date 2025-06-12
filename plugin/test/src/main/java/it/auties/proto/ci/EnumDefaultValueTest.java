package it.auties.proto.ci;

import it.auties.protobuf.annotation.ProtobufDefaultValue;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EnumDefaultValueTest {
    @Test
    public void testBuilder() {
        var message = new EnumDefaultValueTestMessageBuilder()
                .build();
        Assertions.assertEquals(Message.EnumConstant.THIRD, message.constant());
        Assertions.assertEquals(Message.EnumMethod.THIRD, message.method());
    }

    @Test
    public void testDecode() {
        // Create a message with a null value
        var message = new EnumDefaultValueTestMessageBuilder()
                .constant(null)
                .method(null)
                .build();
        var encoded = EnumDefaultValueTestMessageSpec.encode(message); // Encode it
        var decoded = EnumDefaultValueTestMessageSpec.decode(encoded); // Decode it, now it should contain the default value
        Assertions.assertEquals(Message.EnumConstant.THIRD, decoded.constant());
        Assertions.assertEquals(Message.EnumMethod.THIRD, decoded.method());
    }

    @ProtobufMessage
    record Message(
            @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
            EnumConstant constant,
            @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
            EnumMethod method
    ) {
        @ProtobufEnum
        enum EnumConstant {
            FIRST,
            SECOND,
            @ProtobufDefaultValue
            THIRD
        }

        @ProtobufEnum
        enum EnumMethod {
            FIRST,
            SECOND,
            THIRD;

            @ProtobufDefaultValue
            public static EnumMethod defaultValue() {
                return EnumMethod.THIRD;
            }
        }
    }
}

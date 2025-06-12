package it.auties.proto.ci;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.ProtobufString;
import it.auties.protobuf.model.ProtobufType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;

public class MessagePropertyUnknownTest {
    @Test
    public void testSimple() {
        var extendedMessage = new MessagePropertyUnknownTestExtendedMessageBuilder()
                .value(ProtobufString.wrap("Hello World!"))
                .extended(18)
                .build();
        var baseMessage = MessagePropertyUnknownTestSimpleMessageSpec.decode(MessagePropertyUnknownTestExtendedMessageSpec.encode(extendedMessage));
        var unknownField = (Number) baseMessage.unknownFields().get(2);
        Assertions.assertEquals(unknownField.intValue(), extendedMessage.extended());
    }

    @ProtobufMessage
    record SimpleMessage(
            @ProtobufProperty(index = 1, type = ProtobufType.STRING)
            ProtobufString value,
            @ProtobufUnknownFields
            Map<Integer, Object> unknownFields
    ) {

    }

    @ProtobufMessage
    record ExtendedMessage(
            @ProtobufProperty(index = 1, type = ProtobufType.STRING)
            ProtobufString value,
            @ProtobufProperty(index = 2, type = ProtobufType.INT32)
            int extended
    ) {

    }

    @Test
    public void testWrapper() {
        var extendedMessage = new MessagePropertyUnknownTestExtendedMessageBuilder()
                .value(ProtobufString.wrap("Hello World!"))
                .extended(18)
                .build();
        var baseMessage = MessagePropertyUnknownTestWrapperMessageSpec.decode(MessagePropertyUnknownTestExtendedMessageSpec.encode(extendedMessage));
        var unknownField = baseMessage.unknownFields().get(2);
        Assertions.assertTrue(unknownField.isPresent());
        var unknownFieldValue = (Number) unknownField.get();
        Assertions.assertEquals(unknownFieldValue.intValue(), extendedMessage.extended());
    }


    @ProtobufMessage
    record WrapperMessage(
            @ProtobufProperty(index = 1, type = ProtobufType.STRING)
            ProtobufString value,
            @ProtobufUnknownFields
            UnknownFields unknownFields
    ) {
        public static class UnknownFields {
            private final Map<Integer, Object> data;

            public UnknownFields() {
                this.data = new HashMap<>();
            }

            @ProtobufUnknownFields.Setter
            public void put(int key, Object value) {
                data.put(key, value);
            }

            public Optional<Object> get(int key) {
                return Optional.ofNullable(data.get(key));
            }
        }
    }

    @Test
    public void testMixed() {
        var extendedMessage = new MessagePropertyUnknownTestExtendedMessageBuilder()
                .value(ProtobufString.wrap("Hello World!"))
                .extended(18)
                .build();
        var baseMessage = MessagePropertyUnknownTestMixedMessageSpec.decode(MessagePropertyUnknownTestExtendedMessageSpec.encode(extendedMessage));
        var unknownField = (Number) baseMessage.unknownFields().getFirst();
        Assertions.assertEquals(unknownField.intValue(), extendedMessage.extended());
    }

    @ProtobufMessage
    record MixedMessage(
            @ProtobufProperty(index = 1, type = ProtobufType.STRING)
            ProtobufString value,
            @ProtobufUnknownFields(mixins = ListMixin.class)
            List<Object> unknownFields
    ) {
        @ProtobufMixin
        public static class ListMixin {
            @ProtobufDefaultValue
            public static <T> List<T> newList() {
                return new ArrayList<>();
            }

            @ProtobufUnknownFields.Setter
            public static void put(List<Object> list, int key, Object value) {
                list.add(value);
            }
        }
    }
}

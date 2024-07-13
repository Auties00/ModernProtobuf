package it.auties.proto.features.message.unknownFields;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.ProtobufType;

import java.util.*;

@ProtobufMessage
public record MixedMessage(
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String value,
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

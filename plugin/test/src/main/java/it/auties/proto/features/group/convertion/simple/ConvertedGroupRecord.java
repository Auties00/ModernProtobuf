package it.auties.proto.features.group.convertion.simple;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufSerializer;
import it.auties.protobuf.annotation.ProtobufSerializer.GroupProperty;
import it.auties.protobuf.model.ProtobufString;
import it.auties.protobuf.model.ProtobufType;

import java.util.Map;

public record ConvertedGroupRecord(
        ProtobufString string,
        int number
) {
        @ProtobufDeserializer
        public static ConvertedGroupRecord of(Map<Integer, Object> data) {
                return new ConvertedGroupRecord((ProtobufString) data.get(1), (Integer) data.get(2));
        }

        @ProtobufSerializer(groupProperties = {
                @GroupProperty(index = 1, type = ProtobufType.STRING),
                @GroupProperty(index = 2, type = ProtobufType.UINT32)
        })
        public Map<Integer, Object> toData() {
                return Map.of(
                        1, string,
                        2, number
                );
        }
}

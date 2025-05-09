package it.auties.proto.features.group.conversion.nested;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufSerializer;
import it.auties.protobuf.annotation.ProtobufSerializer.GroupProperty;
import it.auties.protobuf.model.ProtobufString;
import it.auties.protobuf.model.ProtobufType;

import java.util.HashMap;
import java.util.Map;

public record NestedConvertedGroupRecord(
        ProtobufString string,
        int number,
        NestedConvertedGroupRecord record
) {
        @ProtobufDeserializer
        public static NestedConvertedGroupRecord of(Map<Integer, Object> data) {
                return new NestedConvertedGroupRecord((ProtobufString) data.get(1), (Integer) data.get(2), (NestedConvertedGroupRecord) data.get(3));
        }

        @ProtobufSerializer(groupProperties = {
                @GroupProperty(index = 1, type = ProtobufType.STRING),
                @GroupProperty(index = 2, type = ProtobufType.UINT32),
                @GroupProperty(index = 3, type = ProtobufType.GROUP, implementation = NestedConvertedGroupRecord.class)
        })
        public Map<Integer, Object> toData() {
                var results = new HashMap<Integer, Object>();
                results.put(1, string);
                results.put(2, number);
                if(record != null) {
                        results.put(3, record);
                }
                return results;
        }
}

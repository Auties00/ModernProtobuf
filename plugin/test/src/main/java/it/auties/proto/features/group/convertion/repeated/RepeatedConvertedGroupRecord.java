package it.auties.proto.features.group.convertion.repeated;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufSerializer;
import it.auties.protobuf.annotation.ProtobufSerializer.GroupProperty;
import it.auties.protobuf.model.ProtobufString;
import it.auties.protobuf.model.ProtobufType;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public record RepeatedConvertedGroupRecord(
        ProtobufString string,
        int number,
        Collection<Integer> repeated
) {
        @SuppressWarnings("unchecked")
        @ProtobufDeserializer
        public static RepeatedConvertedGroupRecord of(Map<Integer, Object> data) {
                return new RepeatedConvertedGroupRecord((ProtobufString) data.get(1), (Integer) data.get(2), (Collection<Integer>) data.get(3));
        }

        @ProtobufSerializer(groupProperties = {
                @GroupProperty(index = 1, type = ProtobufType.STRING),
                @GroupProperty(index = 2, type = ProtobufType.UINT32),
                @GroupProperty(index = 3, type = ProtobufType.UINT32, repeatedValueImplementation = Integer.class, packed = true),
                @GroupProperty(index = 4, type = ProtobufType.UINT32, repeatedValueImplementation = Integer.class)
        })
        public Map<Integer, Object> toData() {
                var results = new HashMap<Integer, Object>();
                results.put(1, string);
                results.put(2, number);
                results.put(3, repeated);
                results.put(4, repeated);
                return results;
        }
}

package it.auties.proto.features.group.convertion.map;

import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufSerializer;
import it.auties.protobuf.annotation.ProtobufSerializer.GroupProperty;
import it.auties.protobuf.model.ProtobufString;
import it.auties.protobuf.model.ProtobufType;

import java.util.HashMap;
import java.util.Map;

public record MapConvertedGroupRecord(
        ProtobufString string,
        int number,
        Map<ProtobufString, ProtobufString> map,
        Map<ProtobufString, Message> anotherMap // Have another map to make sure the generated file is valid syntactically
) {
        @SuppressWarnings("unchecked")
        @ProtobufDeserializer
        public static MapConvertedGroupRecord of(Map<Integer, Object> data) {
                return new MapConvertedGroupRecord((ProtobufString) data.get(1), (Integer) data.get(2), (Map<ProtobufString, ProtobufString>) data.get(3), (Map<ProtobufString, Message>) data.get(4));
        }

        @ProtobufSerializer(groupProperties = {
                @GroupProperty(index = 1, type = ProtobufType.STRING),
                @GroupProperty(index = 2, type = ProtobufType.UINT32),
                @GroupProperty(index = 3, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.STRING),
                @GroupProperty(index = 4, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.OBJECT, mapValueImplementation = Message.class)
        })
        public Map<Integer, Object> toData() {
                var results = new HashMap<Integer, Object>();
                results.put(1, string);
                results.put(2, number);
                if(map != null) {
                        results.put(3, map);
                }
                if(anotherMap != null) {
                        results.put(4, anotherMap);
                }
                return results;
        }

        @ProtobufEnum
        public enum Message {
                FIRST,
                SECOND,
                THIRD
        }
}

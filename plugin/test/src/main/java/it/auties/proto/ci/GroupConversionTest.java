package it.auties.proto.ci;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.ProtobufString;
import it.auties.protobuf.model.ProtobufType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GroupConversionTest {
    @Test
    public void testSimple() {
        var groupRecord = new ConvertedMessageRecord.ConvertedGroupRecord(ProtobufString.wrap("Hello World"), 123);
        var messageRecord = new ConvertedMessageRecord(groupRecord);
        var encoded = GroupConversionTestConvertedMessageRecordSpec.encode(messageRecord);
        var decoded = GroupConversionTestConvertedMessageRecordSpec.decode(encoded);
        Assertions.assertEquals(groupRecord.string(), decoded.record().string());
        Assertions.assertEquals(groupRecord.number(), decoded.record().number());
    }

    @Test
    public void testNested() {
        var child = new NestedConvertedMessageRecord.NestedConvertedGroupRecord(ProtobufString.wrap("Hello World"), 123, null);
        var groupRecord = new NestedConvertedMessageRecord.NestedConvertedGroupRecord(ProtobufString.wrap("Hello World"), 123, child);
        var messageRecord = new NestedConvertedMessageRecord(groupRecord);
        var encoded = GroupConversionTestNestedConvertedMessageRecordSpec.encode(messageRecord);
        var decoded = GroupConversionTestNestedConvertedMessageRecordSpec.decode(encoded);
        Assertions.assertEquals(groupRecord.string(), decoded.record().string());
        Assertions.assertEquals(groupRecord.number(), decoded.record().number());
    }

    @Test
    public void testMap() {
        var groupRecord = new MapConvertedMessageRecord.MapConvertedGroupRecord(
                ProtobufString.wrap("Hello World"),
                123,
                Map.of(ProtobufString.wrap("Hello"), ProtobufString.wrap("World")),
                Map.of(ProtobufString.wrap("Hello"), MapConvertedMessageRecord.MapConvertedGroupRecord.Message.FIRST),
                List.of(MapConvertedMessageRecord.MapConvertedGroupRecord.Message.FIRST)
        );
        var messageRecord = new MapConvertedMessageRecord(groupRecord);
        var encoded = GroupConversionTestMapConvertedMessageRecordSpec.encode(messageRecord);
        var decoded = GroupConversionTestMapConvertedMessageRecordSpec.decode(encoded);
        Assertions.assertEquals(groupRecord.string(), decoded.record().string());
        Assertions.assertEquals(groupRecord.number(), decoded.record().number());
        Assertions.assertEquals(groupRecord.map(), decoded.record().map());
        Assertions.assertEquals(groupRecord.anotherMap(), decoded.record().anotherMap());
        Assertions.assertEquals(groupRecord.messages(), decoded.record().messages());
    }

    @Test
    public void testRepeated() {
        var groupRecord = new RepeatedConvertedMessageRecord.RepeatedConvertedGroupRecord(ProtobufString.wrap("Hello World"), 123, List.of(123));
        var messageRecord = new RepeatedConvertedMessageRecord(groupRecord);
        var encoded = GroupConversionTestRepeatedConvertedMessageRecordSpec.encode(messageRecord);
        var decoded = GroupConversionTestRepeatedConvertedMessageRecordSpec.decode(encoded);
        Assertions.assertEquals(groupRecord.string(), decoded.record().string());
        Assertions.assertEquals(groupRecord.number(), decoded.record().number());
    }

    @ProtobufMessage
    record MapConvertedMessageRecord(
            @ProtobufProperty(index = 1, type = ProtobufType.GROUP)
            MapConvertedGroupRecord record
    ) {
            public record MapConvertedGroupRecord(
                    ProtobufString string,
                    int number,
                    Map<ProtobufString, ProtobufString> map,
                    Map<ProtobufString, Message> anotherMap,
                    List<Message> messages
            ) {
                @SuppressWarnings("unchecked")
                @ProtobufDeserializer
                public static MapConvertedGroupRecord of(Map<Integer, Object> data) {
                    return new MapConvertedGroupRecord((ProtobufString) data.get(1), (Integer) data.get(2), (Map<ProtobufString, ProtobufString>) data.get(3), (Map<ProtobufString, MapConvertedGroupRecord.Message>) data.get(4), (List<MapConvertedGroupRecord.Message>) data.get(5));
                }

                @ProtobufSerializer(groupProperties = {
                        @ProtobufSerializer.GroupProperty(index = 1, type = ProtobufType.STRING),
                        @ProtobufSerializer.GroupProperty(index = 2, type = ProtobufType.UINT32),
                        @ProtobufSerializer.GroupProperty(index = 3, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.STRING),
                        @ProtobufSerializer.GroupProperty(index = 4, type = ProtobufType.MAP, mapKeyType = ProtobufType.STRING, mapValueType = ProtobufType.ENUM, mapValueImplementation = Message.class),
                        @ProtobufSerializer.GroupProperty(index = 5, type = ProtobufType.ENUM, repeatedValueImplementation = Message.class)
                })
                public Map<Integer, Object> toData() {
                    var results = new HashMap<Integer, Object>();
                    results.put(1, string);
                    results.put(2, number);
                    if (map != null) {
                        results.put(3, map);
                    }
                    if (anotherMap != null) {
                        results.put(4, anotherMap);
                    }
                    if(messages != null) {
                        results.put(5, messages);
                    }
                    return results;
                }

                @ProtobufEnum
                public enum Message {
                    FIRST(0),
                    SECOND(1),
                    THIRD(2);

                    final int index;

                    Message(@ProtobufEnumIndex int index) {
                        this.index = index;
                    }
                }
            }
    }

    @ProtobufMessage
    record NestedConvertedMessageRecord(
            @ProtobufProperty(index = 1, type = ProtobufType.GROUP)
            NestedConvertedGroupRecord record
    ) {
        record NestedConvertedGroupRecord(
                ProtobufString string,
                int number,
                NestedConvertedGroupRecord record
        ) {
                @ProtobufDeserializer
                public static NestedConvertedGroupRecord of(Map<Integer, Object> data) {
                        return new NestedConvertedGroupRecord((ProtobufString) data.get(1), (Integer) data.get(2), (NestedConvertedGroupRecord) data.get(3));
                }

                @ProtobufSerializer(groupProperties = {
                        @ProtobufSerializer.GroupProperty(index = 1, type = ProtobufType.STRING),
                        @ProtobufSerializer.GroupProperty(index = 2, type = ProtobufType.UINT32),
                        @ProtobufSerializer.GroupProperty(index = 3, type = ProtobufType.GROUP, implementation = NestedConvertedGroupRecord.class)
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
    }

    @ProtobufMessage
    record RepeatedConvertedMessageRecord(
            @ProtobufProperty(index = 1, type = ProtobufType.GROUP)
            RepeatedConvertedGroupRecord record
    ) {
            record RepeatedConvertedGroupRecord(
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
                            @ProtobufSerializer.GroupProperty(index = 1, type = ProtobufType.STRING),
                            @ProtobufSerializer.GroupProperty(index = 2, type = ProtobufType.UINT32),
                            @ProtobufSerializer.GroupProperty(index = 3, type = ProtobufType.UINT32, repeatedValueImplementation = Integer.class, packed = true),
                            @ProtobufSerializer.GroupProperty(index = 4, type = ProtobufType.UINT32, repeatedValueImplementation = Integer.class)
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
    }

    @ProtobufMessage
    record ConvertedMessageRecord(
            @ProtobufProperty(index = 1, type = ProtobufType.GROUP)
            ConvertedGroupRecord record
    ) {
            record ConvertedGroupRecord(
                    ProtobufString string,
                    int number
            ) {
                    @ProtobufDeserializer
                    public static ConvertedGroupRecord of(Map<Integer, Object> data) {
                            return new ConvertedGroupRecord((ProtobufString) data.get(1), (Integer) data.get(2));
                    }

                    @ProtobufSerializer(groupProperties = {
                            @ProtobufSerializer.GroupProperty(index = 1, type = ProtobufType.STRING),
                            @ProtobufSerializer.GroupProperty(index = 2, type = ProtobufType.UINT32)
                    })
                    public Map<Integer, Object> toData() {
                            return Map.of(
                                    1, string,
                                    2, number
                            );
                    }
            }
    }
}

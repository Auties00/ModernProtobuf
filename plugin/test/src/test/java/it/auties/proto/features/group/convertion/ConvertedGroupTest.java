package it.auties.proto.features.group.convertion;

import it.auties.proto.features.group.convertion.map.MapConvertedGroupRecord;
import it.auties.proto.features.group.convertion.map.MapConvertedMessageRecord;
import it.auties.proto.features.group.convertion.map.MapConvertedMessageRecordSpec;
import it.auties.proto.features.group.convertion.nested.NestedConvertedGroupRecord;
import it.auties.proto.features.group.convertion.nested.NestedConvertedMessageRecord;
import it.auties.proto.features.group.convertion.nested.NestedConvertedMessageRecordSpec;
import it.auties.proto.features.group.convertion.repeated.RepeatedConvertedGroupRecord;
import it.auties.proto.features.group.convertion.repeated.RepeatedConvertedMessageRecord;
import it.auties.proto.features.group.convertion.repeated.RepeatedConvertedMessageRecordSpec;
import it.auties.proto.features.group.convertion.simple.ConvertedMessageRecordSpec;
import it.auties.proto.features.group.simple.GroupRecord;
import it.auties.proto.features.group.simple.MessageRecord;
import it.auties.proto.features.group.simple.MessageRecordSpec;
import it.auties.protobuf.model.ProtobufString;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;
import java.util.List;
import java.util.Map;

public class ConvertedGroupTest {
    @Test
    public void testSimple() {
        var groupRecord = new GroupRecord(ProtobufString.wrap("Hello World"), 123);
        var messageRecord = new MessageRecord(groupRecord);
        var encoded = MessageRecordSpec.encode(messageRecord);
        var decoded = ConvertedMessageRecordSpec.decode(encoded);
        Assertions.assertEquals(groupRecord.string(), decoded.record().string());
        Assertions.assertEquals(groupRecord.number(), decoded.record().number());
    }

    @Test
    public void testNested() {
        var child = new NestedConvertedGroupRecord(ProtobufString.wrap("Hello World"), 123, null);
        var groupRecord = new NestedConvertedGroupRecord(ProtobufString.wrap("Hello World"), 123, child);
        var messageRecord = new NestedConvertedMessageRecord(groupRecord);
        var encoded = NestedConvertedMessageRecordSpec.encode(messageRecord);
        var decoded = NestedConvertedMessageRecordSpec.decode(encoded);
        Assertions.assertEquals(groupRecord.string(), decoded.record().string());
        Assertions.assertEquals(groupRecord.number(), decoded.record().number());
    }

    @Test
    public void testMap() {
        var groupRecord = new MapConvertedGroupRecord(ProtobufString.wrap("Hello World"), 123, null, Map.of(ProtobufString.wrap("Hello"), MapConvertedGroupRecord.Message.FIRST), List.of(MapConvertedGroupRecord.Message.FIRST));
        var messageRecord = new MapConvertedMessageRecord(groupRecord);
        var encoded = MapConvertedMessageRecordSpec.encode(messageRecord);
        var decoded = MapConvertedMessageRecordSpec.decode(encoded);
        Assertions.assertEquals(groupRecord.string(), decoded.record().string());
        Assertions.assertEquals(groupRecord.number(), decoded.record().number());
        Assertions.assertEquals(groupRecord.map(), decoded.record().map());
        Assertions.assertEquals(groupRecord.anotherMap(), decoded.record().anotherMap());
        Assertions.assertEquals(groupRecord.messages(), decoded.record().messages());
    }

    @Test
    public void testRepeated() {
        var groupRecord = new RepeatedConvertedGroupRecord(ProtobufString.wrap("Hello World"), 123, List.of(123));
        var messageRecord = new RepeatedConvertedMessageRecord(groupRecord);
        var encoded = RepeatedConvertedMessageRecordSpec.encode(messageRecord);
        var decoded = RepeatedConvertedMessageRecordSpec.decode(encoded);
        Assertions.assertEquals(groupRecord.string(), decoded.record().string());
        Assertions.assertEquals(groupRecord.number(), decoded.record().number());
    }
}

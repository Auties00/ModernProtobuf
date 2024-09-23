package it.auties.proto.features.group.nested;

import it.auties.protobuf.model.ProtobufString;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class NestedGroupTest {
    @Test
    public void testSimpleTest() {
        var nestedRecord = new NestedGroupRecord(ProtobufString.wrap("Nested"), null);
        var record = new NestedGroupRecord(ProtobufString.wrap("Up"), nestedRecord);
        var message = new MessageRecord(record);
        var encoded = MessageRecordSpec.encode(message);
        var decoded = MessageRecordSpec.decode(encoded);
        Assertions.assertEquals(record.string(), decoded.record().string());
        Assertions.assertEquals(record.record(), decoded.record().record());
    }

    @Test
    public void testMultiTest() {
        var nestedNestedRecord = new NestedGroupRecord(ProtobufString.wrap("Nested2"), null);
        var nestedRecord = new NestedGroupRecord(ProtobufString.wrap("Nested"), nestedNestedRecord);
        var record = new NestedGroupRecord(ProtobufString.wrap("Up"), nestedRecord);
        var message = new MessageRecord(record);
        var encoded = MessageRecordSpec.encode(message);
        var decoded = MessageRecordSpec.decode(encoded);
        Assertions.assertEquals(record.string(), decoded.record().string());
        Assertions.assertEquals(record.record(), decoded.record().record());
    }
}

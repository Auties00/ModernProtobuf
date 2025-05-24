package it.auties.proto.ci;

import it.auties.protobuf.annotation.ProtobufGroup;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufString;
import it.auties.protobuf.model.ProtobufType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

public class GroupTest {
    @Test
    public void testSimple() {
        var nestedRecord = new NestedGroupRecord(ProtobufString.wrap("Nested"), null);
        var record = new NestedGroupRecord(ProtobufString.wrap("Up"), nestedRecord);
        var message = new MessageRecord(record);
        var encoded = GroupTestMessageRecordSpec.encode(message);
        var decoded = GroupTestMessageRecordSpec.decode(encoded);
        Assertions.assertEquals(record.string(), decoded.messageRecordValue().string());
        Assertions.assertEquals(record.record(), decoded.messageRecordValue().record());
    }

    @Test
    public void testWrapped() {
        var nestedRecord = new NestedGroupRecord(ProtobufString.wrap("Nested"), null);
        var record = new NestedGroupRecord(ProtobufString.wrap("Up"), nestedRecord);
        var message = new MixinMessageRecord(Optional.of(record));
        var encoded = GroupTestMixinMessageRecordSpec.encode(message);
        var decoded = GroupTestMixinMessageRecordSpec.decode(encoded);
        var decodedRecord = decoded.messageRecordValue()
                .orElse(null);
        Assertions.assertNotNull(decodedRecord);
        Assertions.assertEquals(record.string(), decodedRecord.string());
        Assertions.assertEquals(record.record(), decodedRecord.record());
    }

    @Test
    public void testMulti() {
        var nestedNestedRecord = new NestedGroupRecord(ProtobufString.wrap("Nested2"), null);
        var nestedRecord = new NestedGroupRecord(ProtobufString.wrap("Nested"), nestedNestedRecord);
        var record = new NestedGroupRecord(ProtobufString.wrap("Up"), nestedRecord);
        var message = new MessageRecord(record);
        var encoded = GroupTestMessageRecordSpec.encode(message);
        var decoded = GroupTestMessageRecordSpec.decode(encoded);
        Assertions.assertEquals(record.string(), decoded.messageRecordValue().string());
        Assertions.assertEquals(record.record(), decoded.messageRecordValue().record());
    }

    @ProtobufMessage
    record MessageRecord(
            @ProtobufProperty(index = 1, type = ProtobufType.GROUP)
            NestedGroupRecord messageRecordValue
    ) {

    }

    @ProtobufMessage
    record MixinMessageRecord(
            @ProtobufProperty(index = 1, type = ProtobufType.GROUP)
            Optional<NestedGroupRecord> messageRecordValue
    ) {

    }

    @ProtobufGroup
    record NestedGroupRecord(
            @ProtobufProperty(index = 1, type = ProtobufType.STRING)
            ProtobufString string,
            @ProtobufProperty(index = 2, type = ProtobufType.GROUP)
            NestedGroupRecord record
    ) {

    }
}

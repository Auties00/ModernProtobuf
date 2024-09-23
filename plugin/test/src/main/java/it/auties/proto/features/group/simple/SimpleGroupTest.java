package it.auties.proto.features.group.simple;

import it.auties.protobuf.model.ProtobufString;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

public class SimpleGroupTest {
    @Test
    public void testSimple() {
        var groupRecord = new GroupRecord(ProtobufString.wrap("Hello World"), 123);
        var messageRecord = new MessageRecord(groupRecord);
        var encoded = MessageRecordSpec.encode(messageRecord);
        var decoded = MessageRecordSpec.decode(encoded);
        Assertions.assertEquals(groupRecord.string(), decoded.record().string());
        Assertions.assertEquals(groupRecord.number(), decoded.record().number());
    }

    @Test
    public void testOptional() {
        var groupRecord = new GroupRecord(ProtobufString.wrap("Hello World"), 123);
        var messageRecord = new OptionalMessageRecord(Optional.of(groupRecord));
        var encoded = OptionalMessageRecordSpec.encode(messageRecord);
        var decoded = OptionalMessageRecordSpec.decode(encoded);
        Assertions.assertEquals(groupRecord.string(), decoded.record().orElseThrow().string());
        Assertions.assertEquals(groupRecord.number(), decoded.record().orElseThrow().number());
    }
}

package it.auties.proto.features.message.missing;

import it.auties.protobuf.model.ProtobufString;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MissingFieldTest {
    @Test
        public void testModifiers() {
        var someMessage = new SerializableMessage(ProtobufString.wrap("Hello"), ProtobufString.wrap("Hello"), ProtobufString.wrap("Hello"), ProtobufString.wrap("Hello"));
        var encoded = SerializableMessageSpec.encode(someMessage);
        var decoded = DeserializableMessageSpec.decode(encoded);
        Assertions.assertEquals(someMessage.content2(), decoded.content2());
    }
}

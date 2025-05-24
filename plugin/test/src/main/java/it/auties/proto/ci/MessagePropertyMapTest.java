package it.auties.proto.ci;

import it.auties.proto.ci.message.map.MapMessageSpec;
import it.auties.protobuf.model.ProtobufString;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class MessagePropertyMapTest {
    @Test
    public void test() {
        var mapMessage = new MapMessage(Map.of(ProtobufString.wrap("abc"), 1, ProtobufString.wrap("def"), 2));
        var encoded = MapMessageSpec.encode(mapMessage);
        Assertions.assertEquals(MapMessageSpec.decode(encoded).content(), mapMessage.content());
    }
}

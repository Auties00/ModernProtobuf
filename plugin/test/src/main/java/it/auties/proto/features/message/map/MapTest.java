package it.auties.proto.features.message.map;

import it.auties.protobuf.model.ProtobufString;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class MapTest {
    @Test
    public void test() {
        var mapMessage = new MapMessage(Map.of(ProtobufString.wrap("abc"), 1, ProtobufString.wrap("def"), 2));
        var encoded = MapMessageSpec.encode(mapMessage);
        Assertions.assertEquals(MapMessageSpec.decode(encoded).content(), mapMessage.content());
    }
}

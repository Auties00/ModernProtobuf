package it.auties.proto.message.map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class MapTest {
    @Test
    public void test() {
        var mapMessage = new MapMessage(Map.of("abc", 1, "def", 2));
        var encoded = MapMessageSpec.encode(mapMessage);
        Assertions.assertEquals(MapMessageSpec.decode(encoded).content(), mapMessage.content());
    }
}

package it.auties.proto.object.message.getter;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class GetterTest {
    @Test
    public void testGetter() {
        var boxMessage = new BoxMessage("Hello World!");
        var encoded = BoxMessageSpec.encode(boxMessage);
        var decoded = BoxMessageSpec.decode(encoded);
        Assertions.assertEquals(decoded.unbox(), boxMessage.unbox());
    }
}

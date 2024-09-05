package it.auties.proto.features.message.getter;

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

    @Test
    public void testMismatch() {
        var boxMessage = new StandaloneGetterMessage("Hello World!");
        var encoded = StandaloneGetterMessageSpec.encode(boxMessage);
        var decoded = StandaloneGetterMessageSpec.decode(encoded);
        Assertions.assertEquals(decoded.tag(), boxMessage.tag());
    }
}

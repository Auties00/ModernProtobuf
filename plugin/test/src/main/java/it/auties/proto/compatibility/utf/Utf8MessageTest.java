package it.auties.proto.compatibility.utf;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class Utf8MessageTest {
    @Test
    @SneakyThrows
    public void testNormal() {
        var someMessage = new UtfMessage("abc");
        var encoded = UtfMessageSpec.encode(someMessage);
        var decoded = UtfMessageSpec.decode(encoded);
        Assertions.assertEquals(someMessage.content(), decoded.content());
    }

    @Test
    @SneakyThrows
    public void testUtf() {
        var someMessage = new UtfMessage("è");
        var encoded = UtfMessageSpec.encode(someMessage);
        var decoded = UtfMessageSpec.decode(encoded);
        Assertions.assertEquals(someMessage.content(), decoded.content());
    }
}

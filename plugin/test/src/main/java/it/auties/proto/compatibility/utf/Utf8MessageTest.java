package it.auties.proto.compatibility.utf;

import it.auties.protobuf.model.ProtobufString;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class Utf8MessageTest {
    @Test
    public void testNormal() {
        var someMessage = new UtfMessage(ProtobufString.wrap("abc"));
        var encoded = UtfMessageSpec.encode(someMessage);
        var decoded = UtfMessageSpec.decode(encoded);
        Assertions.assertEquals(someMessage.content(), decoded.content());
    }

    @Test
    public void testUtf() {
        var someMessage = new UtfMessage(ProtobufString.wrap("è"));
        var encoded = UtfMessageSpec.encode(someMessage);
        var decoded = UtfMessageSpec.decode(encoded);
        Assertions.assertEquals(someMessage.content(), decoded.content());
    }
}

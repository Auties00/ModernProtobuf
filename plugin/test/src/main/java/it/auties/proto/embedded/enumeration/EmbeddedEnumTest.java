package it.auties.proto.embedded.enumeration;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EmbeddedEnumTest {
    @Test
    @SneakyThrows
    public void testModifiers() {
        var anotherMessage = new EnumWrapperMessage(EnumType.SECOND);
        var someMessage = new EmbeddedEnumMessage(anotherMessage);
        var encoded = EmbeddedEnumMessageSpec.encode(someMessage);
        var decoded = EmbeddedEnumMessageSpec.decode(encoded);
        Assertions.assertNotNull(decoded.content());
        Assertions.assertEquals(anotherMessage.type(), decoded.content().type());
    }
}

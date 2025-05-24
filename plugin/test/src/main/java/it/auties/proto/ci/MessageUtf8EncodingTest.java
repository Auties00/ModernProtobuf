package it.auties.proto.ci;

import it.auties.proto.ci.message.utf.UtfMessageSpec;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufString;
import it.auties.protobuf.model.ProtobufType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MessageUtf8EncodingTest {
    @Test
    public void test1Byte() {
        var someMessage = new UtfMessage(ProtobufString.wrap("abc"));
        var encoded = UtfMessageSpec.encode(someMessage);
        var decoded = UtfMessageSpec.decode(encoded);
        Assertions.assertEquals(someMessage.content(), decoded.content());
    }

    @Test
    public void test2Bytes() {
        var someMessage = new UtfMessage(ProtobufString.wrap("ñ"));
        var encoded = UtfMessageSpec.encode(someMessage);
        var decoded = UtfMessageSpec.decode(encoded);
        Assertions.assertEquals(someMessage.content(), decoded.content());
    }

    @Test
    public void test3Bytes() {
        var someMessage = new UtfMessage(ProtobufString.wrap("€"));
        var encoded = UtfMessageSpec.encode(someMessage);
        var decoded = UtfMessageSpec.decode(encoded);
        Assertions.assertEquals(someMessage.content(), decoded.content());
    }

    @Test
    public void test4Bytes() {
        var someMessage = new UtfMessage(ProtobufString.wrap("🌟"));
        var encoded = UtfMessageSpec.encode(someMessage);
        var decoded = UtfMessageSpec.decode(encoded);
        Assertions.assertEquals(someMessage.content(), decoded.content());
    }

    @ProtobufMessage
    record UtfMessage(
            @ProtobufProperty(index = 1, type = ProtobufType.STRING)
            ProtobufString content
    ) {

    }
}

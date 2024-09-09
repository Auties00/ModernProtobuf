package it.auties.proto.features.message.conversion;

import it.auties.protobuf.model.ProtobufString;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class ConversionTest {
    @Test
        public void testModifiers() {
        var someMessage = new WrapperMessage(new Wrapper(ProtobufString.wrap("Hello World")));
        var encoded = WrapperMessageSpec.encode(someMessage);
        var decoded = WrapperMessageSpec.decode(encoded);
        Assertions.assertEquals(someMessage.wrapper().value(), decoded.wrapper().value());
    }

    @Test
        public void testRepeated() {
        var someMessage = new WrappersMessage(new ArrayList<>(List.of(new Wrapper(ProtobufString.wrap("Hello World 1")), new Wrapper(ProtobufString.wrap("Hello World 2")), new Wrapper(ProtobufString.wrap("Hello World 3")))));
        var encoded = WrappersMessageSpec.encode(someMessage);
        var decoded = WrappersMessageSpec.decode(encoded);
        Assertions.assertEquals(someMessage.wrappers(), decoded.wrappers());
    }

}

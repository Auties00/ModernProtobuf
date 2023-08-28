package it.auties.proto.conversion;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class ConversionTest {
    @Test
    @SneakyThrows
    public void testModifiers() {
        var someMessage = new WrapperMessage(new Wrapper("Hello World"));
        var encoded = WrapperMessageSpec.encode(someMessage);
        var decoded = WrapperMessageSpec.decode(encoded);
        Assertions.assertEquals(someMessage.wrapper().value(), decoded.wrapper().value());
    }

    @Test
    @SneakyThrows
    public void testRepeated() {
        var someMessage = new WrappersMessage(new ArrayList<>(List.of(new Wrapper("Hello World 1"), new Wrapper("Hello World 2"), new Wrapper("Hello World 3"))));
        var encoded = WrappersMessageSpec.encode(someMessage);
        var decoded = WrappersMessageSpec.decode(encoded);
        Assertions.assertEquals(someMessage.wrappers(), decoded.wrappers());
    }

}

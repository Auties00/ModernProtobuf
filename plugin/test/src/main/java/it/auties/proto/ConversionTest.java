package it.auties.proto;

import it.auties.protobuf.Protobuf;
import it.auties.protobuf.annotation.ProtobufConverter;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufMessage;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static it.auties.protobuf.model.ProtobufType.STRING;

public class ConversionTest {
    @Test
    @SneakyThrows
    public void testModifiers() {
        var someMessage = new SomeMessage(new Wrapper("Hello World"));
        var encoded = Protobuf.writeMessage(someMessage);
        var decoded = Protobuf.readMessage(encoded, SomeMessage.class);
        Assertions.assertEquals(someMessage.wrapper().value(), decoded.wrapper().value());
    }

    @Test
    @SneakyThrows
    public void testRepeated() {
        var someMessage = new SomeRepeatedMessage(new ArrayList<>(List.of(new Wrapper("Hello World 1"), new Wrapper("Hello World 2"), new Wrapper("Hello World 3"))));
        var encoded = Protobuf.writeMessage(someMessage);
        var decoded = Protobuf.readMessage(encoded, SomeRepeatedMessage.class);
        Assertions.assertEquals(someMessage.wrappers(), decoded.wrappers());
    }

    public record SomeMessage(
            @ProtobufProperty(index = 1, type = STRING)
            Wrapper wrapper
    ) implements ProtobufMessage {

    }

    public record SomeRepeatedMessage(
            @ProtobufProperty(
                    index = 1,
                    type = STRING,
                    repeated = true
            )
            ArrayList<Wrapper> wrappers
    ) implements ProtobufMessage {

    }

    record Wrapper(String value) {
        @ProtobufConverter
        public static Wrapper of(String object) {
            return new Wrapper(object);
        }

        @ProtobufConverter
        public String toValue() {
            return value;
        }
    }
}

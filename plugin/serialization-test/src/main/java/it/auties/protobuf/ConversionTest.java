package it.auties.protobuf;

import it.auties.protobuf.base.ProtobufConverter;
import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufProperty;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static it.auties.protobuf.base.ProtobufType.STRING;

public class ConversionTest implements Protobuf {
    @Test
    @SneakyThrows
    public void testModifiers() {
        var someMessage = new SomeMessage(new Wrapper("Hello World"));
        var encoded = writeValueAsBytes(someMessage);
        var decoded = readMessage(encoded, SomeMessage.class);
        Assertions.assertEquals(someMessage.wrapper().value(), decoded.wrapper().value());
    }
    @Test
    @SneakyThrows
    public void testRepeated() {
        var someMessage = new SomeRepeatedMessage(new ArrayList<>(List.of(new Wrapper("Hello World 1"), new Wrapper("Hello World 2"), new Wrapper("Hello World 3"))));
        var encoded = writeValueAsBytes(someMessage);
        var decoded = readMessage(encoded, SomeRepeatedMessage.class);
        Assertions.assertEquals(someMessage.wrappers(), decoded.wrappers());
    }

    @Jacksonized
    @Builder
    @Data
    @Accessors(fluent = true)
    public static class SomeMessage implements ProtobufMessage {
        @ProtobufProperty(index = 1, type = STRING)
        private Wrapper wrapper;
    }

    @Jacksonized
    @Builder
    @Data
    @Accessors(fluent = true)
    public static class SomeRepeatedMessage implements ProtobufMessage {
        @ProtobufProperty(
                index = 1,
                type = STRING,
                repeated = true
        )
        @Default
        private ArrayList<Wrapper> wrappers = new ArrayList<>();
    }

    record Wrapper(String value) implements ProtobufMessage {
        @ProtobufConverter
        public static Wrapper of(String object){
            return new Wrapper(object);
        }

        @ProtobufConverter
        public String toValue() {
            return value;
        }
    }
}

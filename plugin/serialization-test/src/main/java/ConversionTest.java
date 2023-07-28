import it.auties.protobuf.Protobuf;
import it.auties.protobuf.annotation.ProtobufConverter;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
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
        var someMessage = new SomeRepeatedMessage(new SomeRepeatedMessage.ArrayList1<>(List.of(new Wrapper("Hello World 1"), new Wrapper("Hello World 2"), new Wrapper("Hello World 3"))));
        var encoded = Protobuf.writeMessage(someMessage);
        var decoded = Protobuf.readMessage(encoded, SomeRepeatedMessage.class);
        Assertions.assertEquals(someMessage.wrappers(), decoded.wrappers());
    }

    @Jacksonized
    @Builder
    @Data
    @Accessors(fluent = true)
    @ProtobufMessage
    public static class SomeMessage {
        @ProtobufProperty(index = 1, type = STRING)
        private Wrapper wrapper;
    }

    @Jacksonized
    @Builder
    @Data
    @Accessors(fluent = true)
    @ProtobufMessage
    public static class SomeRepeatedMessage {
        @ProtobufProperty(
                index = 1,
                type = STRING,
                repeated = true
        )
        @Default
        private ArrayList1<Wrapper> wrappers = new ArrayList1<>();

        public static class ArrayList1<E> extends ArrayList<E> {
            public ArrayList1() {
            }

            public ArrayList1(Collection<? extends E> c) {
                super(c);
            }
        }
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

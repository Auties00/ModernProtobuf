package it.auties.protobuf;

import it.auties.protobuf.base.ProtobufConverter;
import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufProperty;
import it.auties.protobuf.serialization.jackson.ProtobufSchema;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static it.auties.protobuf.base.ProtobufType.STRING;

public class ConversionTest implements TestProvider {
    @Test
    @SneakyThrows
    public void testModifiers() {
        var someMessage = new SomeMessage(new Wrapper("Hello World"));
        var encoded = JACKSON.writeValueAsBytes(someMessage);
        var decoded = JACKSON.reader()
                .with(ProtobufSchema.of(SomeMessage.class))
                .readValue(encoded, SomeMessage.class);
        Assertions.assertEquals(someMessage.wrapper().value(), decoded.wrapper().value());
    }

    @Jacksonized
    @Builder
    @Data
    @Accessors(fluent = true)
    public static class SomeMessage implements ProtobufMessage {
        @ProtobufProperty(index = 1, type = STRING)
        private Wrapper wrapper;
    }


    record Wrapper(String value) implements ProtobufMessage {
        @ProtobufConverter
        public static Wrapper of(Object object){
            return new Wrapper(object.toString());
        }

        @ProtobufConverter
        public Object toValue() {
            return value;
        }
    }
}

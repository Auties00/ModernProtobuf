package it.auties.protobuf;

import it.auties.protobuf.api.model.ProtobufMessage;
import it.auties.protobuf.api.model.ProtobufProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class RepeatedEmbeddedMessageTest implements TestProvider {
    @Test
    @SneakyThrows
    public void testModifiers() {
        var anotherMessage = new AnotherMessage(List.of(1, 2, 3));
        var someMessage = new SomeMessage(List.of(anotherMessage, anotherMessage));
        var encoded = JACKSON.writeValueAsBytes(someMessage);
        var decoded = JACKSON.readMessage(encoded, SomeMessage.class);
        Assertions.assertEquals(someMessage.content(), decoded.content());
    }

    @AllArgsConstructor
    @Jacksonized
    @Data
    @Builder
    @Accessors(fluent = true)
    public static class SomeMessage implements ProtobufMessage {
        @ProtobufProperty(
                index = 1,
                type = ProtobufProperty.Type.MESSAGE,
                concreteType = AnotherMessage.class,
                repeated = true
        )
        private List<AnotherMessage> content;

        public static class SomeMessageBuilder {
            public SomeMessageBuilder content(List<AnotherMessage> content){
                if(this.content == null) this.content = new ArrayList<>();
                this.content.addAll(content);
                return this;
            }
        }
    }


    @AllArgsConstructor
    @Jacksonized
    @Data
    @Builder
    @Accessors(fluent = true)
    public static class AnotherMessage implements ProtobufMessage {
        @ProtobufProperty(
                index = 1,
                type = ProtobufProperty.Type.INT32,
                repeated = true
        )
        private List<Integer> content;

        public static class AnotherMessageBuilder {
            public AnotherMessageBuilder content(List<Integer> content){
                if(this.content == null) this.content = new ArrayList<>();
                this.content.addAll(content);
                return this;
            }
        }
    }

}

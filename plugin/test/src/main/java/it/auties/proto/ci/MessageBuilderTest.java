package it.auties.proto.ci;

import it.auties.protobuf.annotation.ProtobufBuilder;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufString;
import org.junit.jupiter.api.Test;

import static it.auties.protobuf.model.ProtobufType.STRING;

public class MessageBuilderTest {
    @Test
    public void testBuilder() {
        var defaultResult = new MessageBuilderTestWrapperMessageBuilder()
                .content(ProtobufString.wrap("123"))
                .build();
        MessageBuilderTestWrapperMessageSpec.encode(defaultResult);
        var constructorResult = new MessageBuilderTestConstructorWrapperMessageBuilder()
                .content(123)
                .build();
        MessageBuilderTestWrapperMessageSpec.encode(constructorResult);
        var staticMethodResult = new MessageBuilderTestStaticWrapperMessageBuilder()
                .content(123)
                .build();
        MessageBuilderTestWrapperMessageSpec.encode(staticMethodResult);
    }

    @ProtobufMessage
    public record WrapperMessage(
            @ProtobufProperty(index = 1, type = STRING)
            ProtobufString content
    ) {
        @ProtobufBuilder(name = "ConstructorWrapperMessageBuilder")
        public WrapperMessage(int content) {
            this(ProtobufString.wrap(String.valueOf(content)));
        }

        @ProtobufBuilder(name = "StaticWrapperMessageBuilder")
        public static WrapperMessage of(int content) {
            return new WrapperMessage(ProtobufString.wrap(String.valueOf(content)));
        }
    }
}

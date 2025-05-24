package it.auties.proto.ci;

import it.auties.proto.ci.message.builder.ConstructorWrapperMessageBuilder;
import it.auties.proto.ci.message.builder.StaticWrapperMessageBuilder;
import it.auties.proto.ci.message.builder.WrapperMessageBuilder;
import it.auties.proto.ci.message.builder.WrapperMessageSpec;
import it.auties.protobuf.annotation.ProtobufBuilder;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufString;
import org.junit.jupiter.api.Test;

import static it.auties.protobuf.model.ProtobufType.STRING;

public class MessageBuilderTest {
    @Test
    public void testBuilder() {
        var defaultResult = new WrapperMessageBuilder()
                .content(ProtobufString.wrap("123"))
                .build();
        WrapperMessageSpec.encode(defaultResult);
        var constructorResult = new ConstructorWrapperMessageBuilder()
                .content(123)
                .build();
        WrapperMessageSpec.encode(constructorResult);
        var staticMethodResult = new StaticWrapperMessageBuilder()
                .content(123)
                .build();
        WrapperMessageSpec.encode(staticMethodResult);
    }

    @ProtobufMessage
    public static record WrapperMessage(
            @ProtobufProperty(index = 1, type = STRING)
            ProtobufString content
    ) {
        @ProtobufBuilder(className = "ConstructorWrapperMessageBuilder")
        public WrapperMessage(int content) {
            this(ProtobufString.wrap(String.valueOf(content)));
        }

        @ProtobufBuilder(className = "StaticWrapperMessageBuilder")
        public static WrapperMessage of(int content) {
            return new WrapperMessage(ProtobufString.wrap(String.valueOf(content)));
        }
    }
}

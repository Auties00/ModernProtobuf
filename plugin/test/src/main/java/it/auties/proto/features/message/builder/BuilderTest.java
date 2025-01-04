package it.auties.proto.features.message.builder;

import it.auties.protobuf.model.ProtobufString;
import org.junit.jupiter.api.Test;

public class BuilderTest {
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
}

package it.auties.proto.message.builder;

import org.junit.jupiter.api.Test;

public class BuilderTest {
    @Test
    public void testBuilder() {
        var result = new SimpleWrapperMessageBuilder()
                .content(123)
                .build();
        WrapperMessageSpec.encode(result);
    }
}

package it.auties.proto.ci;

import it.auties.proto.ci.message.embeddedRepeated.RepeatedWrapperMessageSpec;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class RepeatedEmbeddedMessageTest {
    @Test
        public void testModifiers() {
        var finalMessage = new RepeatedWrapperMessage.RepeatedEmbeddedWrapperMessage.RepeatedEmbeddedMessage(new ArrayList<>(List.of(1, 2, 3)));
        var anotherFinalMessage = new RepeatedWrapperMessage.RepeatedEmbeddedWrapperMessage.RepeatedEmbeddedMessage(new ArrayList<>(List.of(4, 5, 6)));
        var lastFinalMessage = new RepeatedWrapperMessage.RepeatedEmbeddedWrapperMessage.RepeatedEmbeddedMessage(new ArrayList<>(List.of(7, 8, 9)));
        var anotherMessage = new RepeatedWrapperMessage.RepeatedEmbeddedWrapperMessage(new ArrayList<>(List.of(finalMessage, anotherFinalMessage, lastFinalMessage)));
        var anotherAnotherMessage = new RepeatedWrapperMessage.RepeatedEmbeddedWrapperMessage(new ArrayList<>(List.of(anotherFinalMessage)));
        var someMessage = new RepeatedWrapperMessage(new ArrayList<>(List.of(anotherMessage, anotherAnotherMessage)));
        var encoded = RepeatedWrapperMessageSpec.encode(someMessage);
        var decoded = RepeatedWrapperMessageSpec.decode(encoded);
        Assertions.assertEquals(someMessage.content(), decoded.content());
    }

    @ProtobufMessage
    public static record RepeatedWrapperMessage(
            @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
            ArrayList<RepeatedEmbeddedWrapperMessage> content
    ) {

            @ProtobufMessage
            public static record RepeatedEmbeddedWrapperMessage(
                    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
                    ArrayList<RepeatedEmbeddedMessage> contentAbc
            ) {

                    @ProtobufMessage
                    public static record RepeatedEmbeddedMessage(
                            @ProtobufProperty(index = 1, type = ProtobufType.INT32)
                            ArrayList<Integer> content
                    ) {

                    }
            }
    }
}

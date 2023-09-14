package it.auties.proto.message.repeated;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class RepeatedTest {
    @Test
        public void testModifiers() throws InvalidProtocolBufferException {
        var repeatedMessage = new ModernRepeatedMessage(new ArrayList<>(List.of(1, 2, 3)));
        var encoded = ModernRepeatedMessageSpec.encode(repeatedMessage);
        var oldDecoded = RepeatedMessage.parseFrom(encoded);
        var modernDecoded = ModernRepeatedMessageSpec.decode(encoded);
        Assertions.assertEquals(repeatedMessage.content(), modernDecoded.content());
        Assertions.assertEquals(oldDecoded.getContentList(), modernDecoded.content());
    }

    @Test
        public void testMultipleModifiers(){
        var repeatedMessage = new ModernBetaRepeatedMessage(new ArrayList<>(List.of(1, 2, 3)), new ArrayList<>(List.of(new ModernRepeatedMessage(new ArrayList<>(List.of(1, 2, 3))))));
        var encoded = ModernBetaRepeatedMessageSpec.encode(repeatedMessage);
        var modernDecoded = ModernBetaRepeatedMessageSpec.decode(encoded);
        Assertions.assertEquals(repeatedMessage.content(), modernDecoded.content());
    }
}

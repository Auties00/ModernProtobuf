import it.auties.protobuf.Protobuf;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static it.auties.protobuf.model.ProtobufType.UINT32;

public class PackedTest {
    @Test
    @SneakyThrows
    public void testModifiers() {
        var someMessage = new SomeMessage(new ArrayList<>(List.of(1, 2, 3)));
        var encoded =  Protobuf.writeMessage(someMessage);
        var decoded = Protobuf.readMessage(encoded, SomeMessage.class);
        Assertions.assertEquals(someMessage.content(), decoded.content());
    }

    @ProtobufMessage
    public record SomeMessage(
            @ProtobufProperty(index = 1, type = UINT32, repeated = true) ArrayList<Integer> content
    ) {

    }
}

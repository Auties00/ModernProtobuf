package it.auties.proto;

import it.auties.protobuf.Protobuf;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.exception.ProtobufSerializationException;
import it.auties.protobuf.model.ProtobufMessage;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static it.auties.protobuf.model.ProtobufType.STRING;

public class ModifierTest {
    @Test
    @SneakyThrows
    public void testModifiers() {
        var missing = new RequiredMessage(null, null);
        Assertions.assertFalse(encode(missing), "Missing mandatory field passed encoding");
        var correct = new RequiredMessage("something", "something");
        Assertions.assertTrue(encode(correct), "Correct fields didn't pass compilation");
        var alternated = new RequiredMessage("something", null);
        Assertions.assertTrue(encode(alternated), "Alternated fields didn't pass compilation");
    }

    private boolean encode(ProtobufMessage object) {
        try {
            Protobuf.writeMessage(object);
            return true;
        } catch (ProtobufSerializationException exception) {
            return false;
        }
    }

    public record RequiredMessage(
            @ProtobufProperty(index = 1, type = STRING, required = true)
            String required,
            @ProtobufProperty(index = 2, type = STRING) String optional
    ) implements ProtobufMessage {

    }
}

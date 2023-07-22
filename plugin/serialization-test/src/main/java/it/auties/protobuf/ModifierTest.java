package it.auties.protobuf;

import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufProperty;
import it.auties.protobuf.base.ProtobufSerializationException;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it.auties.protobuf.base.ProtobufType.STRING;

public class ModifierTest implements Protobuf {
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

    private boolean encode(ProtobufMessage object) throws IOException {
        try {
            writeValueAsBytes(object);
            return true;
        } catch (ProtobufSerializationException exception) {
            return false;
        }
    }

    @Jacksonized
    @Builder
    @Data
    @Accessors(fluent = true)
    public static class RequiredMessage implements ProtobufMessage {
        @ProtobufProperty(index = 1, type = STRING, required = true)
        // @NonNull (Removed for testing purposes)
        private String required;

        @ProtobufProperty(index = 2, type = STRING)
        private String optional;
    }
}
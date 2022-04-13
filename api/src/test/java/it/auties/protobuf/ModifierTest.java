package it.auties.protobuf;

import com.fasterxml.jackson.databind.JsonMappingException;
import it.auties.protobuf.api.model.ProtobufMessage;
import it.auties.protobuf.api.model.ProtobufProperty;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class ModifierTest implements TestProvider {
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

    private boolean encode(Object object) throws IOException {
        try {
            JACKSON.writeValueAsBytes(object);
            return true;
        }catch (JsonMappingException exception){
            return false;
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Jacksonized
    @Data
    @Builder
    @Accessors(fluent = true)
    public static class RequiredMessage implements ProtobufMessage {
        @ProtobufProperty(
                index = 1,
                type = ProtobufProperty.Type.STRING,
                required = true
        )
        // @NonNull (Removed for testing purposes)
        private String required;

        @ProtobufProperty(
                index = 2,
                type = ProtobufProperty.Type.STRING
        )
        private String optional;
    }
}

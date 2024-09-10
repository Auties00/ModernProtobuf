package it.auties.proto.features.message.modifier;

import it.auties.protobuf.model.ProtobufString;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ModifierTest {
    @Test
        public void testModifiers() {
        var missing = new RequiredMessage(null, null);
        Assertions.assertFalse(encode(missing), "Missing mandatory field passed encoding");
        var correct = new RequiredMessage(ProtobufString.wrap("something"), ProtobufString.wrap("something"));
        Assertions.assertTrue(encode(correct), "Correct fields didn't pass compilation");
        var alternated = new RequiredMessage(ProtobufString.wrap("something"), null);
        Assertions.assertTrue(encode(alternated), "Alternated fields didn't pass compilation");
    }

    private boolean encode(RequiredMessage object) {
        try {
            RequiredMessageSpec.encode(object);
            return true;
        } catch (Throwable exception) {
            return false;
        }
    }

}

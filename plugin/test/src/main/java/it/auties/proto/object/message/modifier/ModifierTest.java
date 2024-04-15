package it.auties.proto.object.message.modifier;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ModifierTest {
    @Test
        public void testModifiers() {
        var missing = new RequiredMessage(null, null);
        Assertions.assertFalse(encode(missing), "Missing mandatory field passed encoding");
        var correct = new RequiredMessage("something", "something");
        Assertions.assertTrue(encode(correct), "Correct fields didn't pass compilation");
        var alternated = new RequiredMessage("something", null);
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

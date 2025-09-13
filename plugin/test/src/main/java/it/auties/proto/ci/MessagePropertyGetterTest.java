package it.auties.proto.ci;

import it.auties.protobuf.annotation.ProtobufAccessor;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufString;
import it.auties.protobuf.model.ProtobufType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Objects;

public class MessagePropertyGetterTest {
    @Test
    public void testGetter() {
        var boxMessage = new BoxMessage(ProtobufString.wrap("Hello World!"));
        var encoded = MessagePropertyGetterTestBoxMessageSpec.encode(boxMessage);
        var decoded = MessagePropertyGetterTestBoxMessageSpec.decode(encoded);
        Assertions.assertEquals(decoded.unbox(), boxMessage.unbox());
    }

    @Test
    public void testMismatch() {
        var boxMessage = new StandaloneGetterMessage(ProtobufString.wrap("Hello World!"));
        var encoded = MessagePropertyGetterTestStandaloneGetterMessageSpec.encode(boxMessage);
        var decoded = MessagePropertyGetterTestStandaloneGetterMessageSpec.decode(encoded);
        Assertions.assertEquals(decoded.tag(), boxMessage.tag());
    }

    @ProtobufMessage
    static final class BoxMessage {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        private final ProtobufString value;

        public BoxMessage(ProtobufString value) {
            this.value = value;
        }

        @ProtobufAccessor(index = 1)
        public ProtobufString unbox() {
            return value;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (BoxMessage) obj;
            return Objects.equals(this.value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }

        @Override
        public String toString() {
            return "GetterMessage[" +
                    "value=" + value + ']';
        }
    }

    @ProtobufMessage
    static final class StandaloneGetterMessage {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        private final ProtobufString value;

        public StandaloneGetterMessage(ProtobufString value) {
            this.value = value;
        }

        @ProtobufAccessor(index = 1)
        public ProtobufString unbox() {
            return value;
        }

        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        public ProtobufString tag() {
            return value;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (StandaloneGetterMessage) obj;
            return Objects.equals(this.value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }

        @Override
        public String toString() {
            return "GetterMessage[" +
                    "value=" + value + ']';
        }
    }
}

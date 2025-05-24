package it.auties.proto.ci;

import it.auties.proto.ci.message.defaltValue.WrapperMessageBuilder;
import it.auties.proto.ci.message.defaltValue.WrapperMessageSpec;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.ProtobufString;
import it.auties.protobuf.model.ProtobufType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Objects;

public class MessageDefaultValueTest {
    @Test
    public void testDefaultValue() {
        var defaultMessage = new WrapperMessageBuilder()
                .build();
        Assertions.assertEquals(defaultMessage.optionalMessage(), WrapperMessage.OptionalMessage.empty());
        var encoded = WrapperMessageSpec.encode(new WrapperMessage(null));
        var decoded = WrapperMessageSpec.decode(encoded);
        Assertions.assertEquals(decoded.optionalMessage(), WrapperMessage.OptionalMessage.empty());
    }

    @ProtobufMessage
    public static record WrapperMessage(
            @ProtobufProperty(index = 1, type = ProtobufType.STRING)
            OptionalMessage optionalMessage
    ) {

            public static final class OptionalMessage {
                private static final OptionalMessage EMPTY = new OptionalMessage(null);

                private final ProtobufString value;
                private OptionalMessage(ProtobufString value) {
                    this.value = value;
                }

                @ProtobufDefaultValue
                public static OptionalMessage empty() {
                    return EMPTY;
                }

                @ProtobufDeserializer
                public static OptionalMessage ofNullable(ProtobufString value) {
                    return value == null ? EMPTY : new OptionalMessage(value);
                }

                @ProtobufSerializer
                public ProtobufString value() {
                    return value;
                }

                @Override
                public boolean equals(Object obj) {
                    if (obj == this) return true;
                    if (obj == null || obj.getClass() != this.getClass()) return false;
                    var that = (OptionalMessage) obj;
                    return Objects.equals(this.value, that.value);
                }

                @Override
                public int hashCode() {
                    return Objects.hash(value);
                }

                @Override
                public String toString() {
                    return "OptionalMessage[" +
                            "value=" + value + ']';
                }
            }
    }
}

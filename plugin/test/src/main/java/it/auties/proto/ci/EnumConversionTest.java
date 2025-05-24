package it.auties.proto.ci;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.ProtobufType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class EnumConversionTest {
    @Test
    public void test() {
        var message = new Message(Message.Uint32.DAY);
        var encoded = EnumConversionTestMessageSpec.encode(message);
        var decoded = EnumConversionTestMessageSpec.decode(encoded);
        Assertions.assertEquals(message.duration(), decoded.duration());
    }

    @ProtobufMessage
    public record Message(
            @ProtobufProperty(index = 1, type = ProtobufType.UINT32)
            Uint32 duration
    ) {
            public enum Uint32 {
                @ProtobufDefaultValue
                ZERO(Duration.ofSeconds(0)),
                DAY(Duration.ofDays(1)),
                WEEK(Duration.ofDays(7)),
                MONTH(Duration.ofDays(30));

                private static final Map<Integer, Uint32> VALUES = Arrays.stream(Uint32.values())
                        .collect(Collectors.toMap(Uint32::duration, Function.identity()));

                private final Duration duration;
                Uint32(Duration duration) {
                    this.duration = duration;
                }

                @ProtobufDeserializer
                public static Uint32 of(int duration) {
                    return VALUES.get(duration);
                }

                @ProtobufSerializer
                public int duration() {
                    return Math.toIntExact(duration.toSeconds());
                }
            }
    }
}

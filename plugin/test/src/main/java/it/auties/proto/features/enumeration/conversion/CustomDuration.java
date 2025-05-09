package it.auties.proto.features.enumeration.conversion;

import it.auties.protobuf.annotation.ProtobufDefaultValue;
import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufSerializer;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum CustomDuration {
    @ProtobufDefaultValue
    ZERO(Duration.ofSeconds(0)),
    DAY(Duration.ofDays(1)),
    WEEK(Duration.ofDays(7)),
    MONTH(Duration.ofDays(30));

    private static final Map<Integer, CustomDuration> VALUES = Arrays.stream(values())
            .collect(Collectors.toMap(CustomDuration::duration, Function.identity()));

    private final Duration duration;
    CustomDuration(Duration duration) {
        this.duration = duration;
    }

    @ProtobufDeserializer
    public static CustomDuration of(int duration) {
        return VALUES.get(duration);
    }

    @ProtobufSerializer
    public int duration() {
        return Math.toIntExact(duration.toSeconds());
    }
}

package it.auties.proto.features.message.defaltValue;

import it.auties.protobuf.annotation.ProtobufDefaultValue;
import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufString;
import it.auties.protobuf.model.ProtobufType;

import java.util.Objects;

@ProtobufMessage
public final class OptionalMessage {
    private static final OptionalMessage EMPTY = new OptionalMessage(null);

    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
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
        return "WrapperMessage[" +
                "value=" + value + ']';
    }
}

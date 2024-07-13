package it.auties.proto.features.message.getter;

import it.auties.protobuf.annotation.ProtobufGetter;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Objects;

@ProtobufMessage
public final class BoxMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    private final String value;

    public BoxMessage(String value) {
        this.value = value;
    }

    @ProtobufGetter(index = 1)
    public String unbox() {
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

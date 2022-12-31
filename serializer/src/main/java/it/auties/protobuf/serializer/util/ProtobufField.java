package it.auties.protobuf.serializer.util;

import it.auties.protobuf.base.ProtobufMessage;
import it.auties.protobuf.base.ProtobufProperty;
import it.auties.protobuf.base.ProtobufType;
import it.auties.protobuf.serializer.exception.ProtobufSerializationException;

import java.util.Objects;

public record ProtobufField(String name, int index, ProtobufType type, Class<? extends ProtobufMessage> messageType,
                            Object value, boolean packed, boolean required, boolean repeated,
                            boolean requiresConversion) {
    public ProtobufField(String name, Object value, ProtobufProperty property) {
        this(name, property.index(), property.type(), null, value, property.packed(), property.required(), property.repeated(), false);
    }

    public ProtobufField(String name, Class<? extends ProtobufMessage> type, boolean requiresConversion, ProtobufProperty property) {
        this(name, property.index(), property.type(), type, null, property.packed(), property.required(), property.repeated(), requiresConversion);
    }

    public ProtobufField toSingleField(Object value) {
        return new ProtobufField(name, index, type, messageType, value, packed, required, false, false);
    }

    @SuppressWarnings("unchecked")
    public <T> T dynamicValue() {
        try {
            return (T) value;
        } catch (ClassCastException exception) {
            throw new RuntimeException("A field misreported its own type in a schema: %s".formatted(this), exception);
        }
    }

    public boolean isValid() {
        if (required && value == null) {
            throw new ProtobufSerializationException("Erroneous field at index %s with type %s(%s): missing mandatory value".formatted(index, type, Objects.toString(messageType)));
        }

        return value != null;
    }
}

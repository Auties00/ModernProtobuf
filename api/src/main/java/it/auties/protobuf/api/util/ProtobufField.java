package it.auties.protobuf.api.util;

import it.auties.protobuf.api.exception.ProtobufSerializationException;
import it.auties.protobuf.api.model.ProtobufMessage;
import it.auties.protobuf.api.model.ProtobufProperty;

import java.util.Objects;

import static it.auties.protobuf.api.model.ProtobufProperty.Type.MESSAGE;

public record ProtobufField(String name, int index, ProtobufProperty.Type type,
                            Class<? extends ProtobufMessage> messageType, Object value, boolean packed,
                            boolean required, boolean repeated, boolean requiresConversion) {
    public ProtobufField(String name, int index, ProtobufProperty.Type type, Object value,
                         boolean packed, boolean required, boolean repeated, boolean requiresConversion) {
        this(name, index, type, null, value, packed, required, repeated, requiresConversion);
    }

    public ProtobufField withValue(Object value) {
        return new ProtobufField(name, index, type, messageType,
                value, packed, required, false, false);
    }

    @SuppressWarnings("unchecked")
    public <T> T valueAs() {
        try {
            return (T) value;
        } catch (ClassCastException exception) {
            throw new RuntimeException("A field misreported its own type in a schema: %s".formatted(this), exception);
        }
    }

    public boolean valid() {
        if(type == MESSAGE && messageType == null){
            throw new ProtobufSerializationException(("Erroneous field at index %s with type %s: cannot detect the message type. ".formatted(index, type) +
                    "Usually this means that your model clas isn't specified or doesn't implement ProtobufModel"));
        }

        if (required && value == null) {
            throw new ProtobufSerializationException("Erroneous field at index %s with type %s(%s): missing mandatory value"
                    .formatted(index, type, Objects.toString(messageType)));
        }

        return value != null;
    }
}

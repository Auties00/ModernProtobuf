package it.auties.proto.features.message.unknownFields;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.annotation.ProtobufUnknownFields;
import it.auties.protobuf.model.ProtobufString;
import it.auties.protobuf.model.ProtobufType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@ProtobufMessage
public record WrapperMessage(
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        ProtobufString value,
        @ProtobufUnknownFields
        UnknownFields unknownFields
) {
    public static class UnknownFields {
        private final Map<Integer, Object> data;

        public UnknownFields() {
            this.data = new HashMap<>();
        }

        @ProtobufUnknownFields.Setter
        public void put(int key, Object value) {
            data.put(key, value);
        }

        public Optional<Object> get(int key) {
                return Optional.ofNullable(data.get(key));
        }
    }
}

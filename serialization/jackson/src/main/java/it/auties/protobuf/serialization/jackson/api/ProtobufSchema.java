package it.auties.protobuf.serialization.jackson.api;

import com.fasterxml.jackson.core.FormatSchema;
import it.auties.protobuf.base.ProtobufMessage;
import lombok.NonNull;

public record ProtobufSchema(@NonNull Class<? extends ProtobufMessage> messageType) implements FormatSchema {
    public static ProtobufSchema of(Class<? extends ProtobufMessage> schema) {
        return new ProtobufSchema(schema);
    }

    @Override
    public String getSchemaType() {
        return "protobuf";
    }
}

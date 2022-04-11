package it.auties.protobuf.model;

import com.fasterxml.jackson.core.FormatSchema;
import lombok.NonNull;

public record ProtobufSchema(@NonNull Class<? extends ProtobufMessage> messageType) implements FormatSchema {
    public static ProtobufSchema of(Class<? extends ProtobufMessage> schema){
        return new ProtobufSchema(schema);
    }

    @Override
    public String getSchemaType() {
        return "protobuf";
    }
}

package it.auties.protobuf.serialization.performance.model;

import lombok.NonNull;

import java.util.Objects;

public record ProtobufTypeImplementation(@NonNull String rawType, String parameterType) {
    public String parameterType(){
        return Objects.requireNonNullElse(parameterType, rawType);
    }
}

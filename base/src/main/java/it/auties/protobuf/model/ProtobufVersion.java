package it.auties.protobuf.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

import java.util.Arrays;
import java.util.Optional;

@Getter
@AllArgsConstructor
@Accessors(fluent = true)
public enum ProtobufVersion {
    PROTOBUF_2("proto2"),
    PROTOBUF_3("proto3");

    private final String versionCode;

    public static Optional<ProtobufVersion> of(@NonNull String name) {
        return Arrays.stream(values())
                .filter(entry -> name.contains(entry.versionCode()))
                .findFirst();
    }
}

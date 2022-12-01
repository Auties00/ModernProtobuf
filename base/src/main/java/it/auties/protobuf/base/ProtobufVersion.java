package it.auties.protobuf.base;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

import java.util.Arrays;
import java.util.Optional;

@AllArgsConstructor
@Accessors(fluent = true)
public enum ProtobufVersion {
    PROTOBUF_2("proto2"),
    PROTOBUF_3("proto3");

    @Getter
    private final String versionCode;

    public static ProtobufVersion defaultVersion() {
        return PROTOBUF_2;
    }

    public static Optional<ProtobufVersion> forName(@NonNull String name) {
        return Arrays.stream(values())
                .filter(entry -> name.contains(entry.versionCode()))
                .findFirst();
    }
}
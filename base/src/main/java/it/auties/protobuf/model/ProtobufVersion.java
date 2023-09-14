package it.auties.protobuf.model;

import java.util.Arrays;
import java.util.Optional;

public enum ProtobufVersion {
    PROTOBUF_2("proto2"),
    PROTOBUF_3("proto3");

    private final String versionCode;
    ProtobufVersion(String versionCode) {
        this.versionCode = versionCode;
    }

    public static Optional<ProtobufVersion> of(String name) {
        return Arrays.stream(values())
                .filter(entry -> name.contains(entry.versionCode()))
                .findFirst();
    }

    public static ProtobufVersion defaultVersion() {
        return PROTOBUF_2;
    }

    public String versionCode() {
        return this.versionCode;
    }
}

package it.auties.protobuf.model;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum ProtobufVersion {
    PROTOBUF_2("proto2"),
    PROTOBUF_3("proto3");

    private static final ProtobufVersion DEFAULT_VERSION = PROTOBUF_2;
    private static final Map<String, ProtobufVersion> BY_VERSION_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(entry -> entry.versionCode.toLowerCase(), Function.identity()));

    private final String versionCode;
    ProtobufVersion(String versionCode) {
        this.versionCode = versionCode;
    }

    public static Optional<ProtobufVersion> of(String name) {
        return Optional.ofNullable(BY_VERSION_CODE.get(name));
    }

    public static ProtobufVersion defaultVersion() {
        return DEFAULT_VERSION;
    }

    public String versionCode() {
        return this.versionCode;
    }

    @Override
    public String toString() {
        return versionCode();
    }
}

package it.auties.protobuf.serialization.jackson.model;

import com.fasterxml.jackson.core.Version;

public class VersionInfo {
    public static Version current() {
        return new Version(1, 11, 0, null,
                "com.github.auties00", "protobuf-api");
    }
}
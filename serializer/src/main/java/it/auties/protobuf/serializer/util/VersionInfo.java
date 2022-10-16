package it.auties.protobuf.serializer.util;

import com.fasterxml.jackson.core.Version;

public class VersionInfo {
    public static Version current() {
        return new Version(1, 11, 0, null,
                "com.github.auties00", "protobuf-api");
    }
}

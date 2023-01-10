package it.auties.protobuf.serialization.jackson;

import com.fasterxml.jackson.core.Version;

public class ProtobufVersionInfo {
    public static Version current() {
        return new Version(1, 19, 0, null,
                "com.github.auties00", "protobuf-serializer-jackson");
    }
}

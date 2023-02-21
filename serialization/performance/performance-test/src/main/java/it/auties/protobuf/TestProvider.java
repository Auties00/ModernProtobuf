package it.auties.protobuf;

import it.auties.protobuf.serialization.performance.Protobuf;

import java.io.IOException;

public interface TestProvider {
    default <T> T readMessage(byte[] message, Class<T> clazz) throws IOException {
        return Protobuf.readMessage(message, clazz);
    }

    default byte[] writeValueAsBytes(Object object) throws IOException {
        return Protobuf.writeMessage(object);
    }
}
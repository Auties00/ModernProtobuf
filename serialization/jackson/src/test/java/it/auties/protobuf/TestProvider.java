package it.auties.protobuf;

import it.auties.protobuf.serialization.jackson.api.ProtobufMapper;

public interface TestProvider {
    ProtobufMapper JACKSON = new ProtobufMapper();
}

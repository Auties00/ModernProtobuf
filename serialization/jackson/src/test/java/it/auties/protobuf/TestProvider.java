package it.auties.protobuf;

import it.auties.protobuf.serialization.jackson.ProtobufMapper;

public interface TestProvider {
    ProtobufMapper JACKSON = new ProtobufMapper();
}

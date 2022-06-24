package it.auties.protobuf;

import it.auties.protobuf.api.jackson.ProtobufMapper;

public interface TestProvider {
    ProtobufMapper JACKSON = new ProtobufMapper();
}
